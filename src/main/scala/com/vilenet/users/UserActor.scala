package com.vilenet.users

import akka.actor.{Terminated, ActorRef, Props}
import akka.io.Tcp.{Received, Write}
import com.vilenet.Constants._
import com.vilenet.connection.WriteOut
import com.vilenet.ViLeNetActor
import com.vilenet.channels._
import com.vilenet.coders._
import com.vilenet.coders.binary.BinaryChatEncoder
import com.vilenet.coders.telnet._
import com.vilenet.utils.{CaseInsensitiveHashSet, CaseInsensitiveFiniteHashSet}

/**
 * Created by filip on 9/27/15.
 */
object UserActor {
  def apply(connection: ActorRef, user: User, protocol: Protocol) = Props(new UserActor(connection, user,
    protocol match {
      case BinaryProtocol => BinaryChatEncoder
      case TelnetProtocol => TelnetEncoder
  }))
}

case object GetUser
case class UserUpdated(user: User)


class UserActor(connection: ActorRef, var user: User, encoder: Encoder) extends ViLeNetActor {

  var channelActor: ActorRef = _
  var squelchedUsers = CaseInsensitiveHashSet()

  context.watch(connection)

  def checkSquelched(user: User) = {
    if (squelchedUsers.contains(user.name)) {
      Flags.squelch(user)
    } else {
      Flags.unsquelch(user)
    }
  }

  def encodeAndSend(chatEvent: ChatEvent) = {
    encoder(chatEvent)
      .fold()(message => connection ! WriteOut(message))
  }

  override def receive: Receive = {
    case UserUpdated(newUser) =>
      user = newUser

    case UserSquelched(username) =>
      squelchedUsers += username

    case UserUnsquelched(username) =>
      squelchedUsers -= username

    case UserIn(user) =>
      encodeAndSend(UserIn(checkSquelched(user)))

    case UserJoined(user) =>
      encodeAndSend(UserJoined(checkSquelched(user)))

    case channelEvent: SquelchableTalkEvent =>
      if (!squelchedUsers.contains(channelEvent.user.name)) {
        encodeAndSend(channelEvent)
      }

    case channelEvent: ChatEvent =>
      channelEvent match {
        case UserChannel(newUser, channel, channelActor) =>
          user = newUser
          this.channelActor = channelActor
        case _ =>
      }
      encodeAndSend(channelEvent)

    case (actor: ActorRef, WhisperMessage(fromUser, toUsername, message)) =>
      encoder(UserWhisperedFrom(fromUser, message))
        .fold()(msg => {
          connection ! WriteOut(msg)
          actor !  UserWhisperedTo(user, message)
        })

    case (actor: ActorRef, WhoisCommand(fromUser, username)) =>
      actor ! UserInfo(s"${user.name} is using ${WhoamiCommand.encodeClient(user.client)} in the channel ${user.channel}.")

    case BanCommand(kicking) =>
      self ! UserInfo(YOU_KICKED(kicking))
      channelsActor ! UserSwitchedChat(self, user, "The Void")

    case KickCommand(kicking) =>
      self ! UserInfo(YOU_KICKED(kicking))
      channelsActor ! UserSwitchedChat(self, user, "The Void")

    case Received(data) =>
      UserMessageDecoder(user, data) match {
        case command: Command =>
          log.error(s"UserMessageDecoder $command")
          command match {
            /**
             * The channel command and user command have two different flows.
             *  A user has to go through a middle-man users actor because there is no guarantee the receiving user is online.
             *  A command being sent to the user's channel can be done via actor selection, since we can guarantee the
             *  channel exists.
             */
            case JoinUserCommand(fromUser, channel) =>
              if (!user.channel.equalsIgnoreCase(channel)) {
                channelsActor ! UserSwitchedChat(self, fromUser, channel)
              }
            case command: ChannelCommand => channelActor ! command
            case command: UserToChannelCommand => usersActor ! command
            case command: UserCommand => usersActor ! command
            case command: ReturnableCommand => encoder(command).fold()(connection ! WriteOut(_))
            case _ =>
          }
        case x =>
          //log.error(s"### UserMessageDecoder Unhandled: $x")
      }

    case command: UserToChannelCommandAck =>
      log.error(s"UTCCA $command")
      channelActor ! command

    case Terminated(actor) =>
      usersActor ! Rem(user.name)
      context.stop(self)

    case x =>
      //log.error(s"### UserActor Unhandled: $x")
  }

  def isOperator(user: User) = (user.flags & 0x02) == 0x02 || (user.flags & 0x01) == 0x01
}
