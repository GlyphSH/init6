package com.init6.channels

import akka.actor.{ActorRef, Address}
import com.init6.Constants._
import com.init6.coders.commands.{DesignateCommand, OperableCommand}
import com.init6.users.{UserToChannelCommandAck, UserUpdated}

import scala.collection.mutable

/**
  * Created by filip on 11/24/15.
  */
trait OperableChannelActor extends ChannelActor {

  val designatedActors = mutable.HashMap[ActorRef, ActorRef]()

  override def receiveEvent = ({
    case command: UserToChannelCommandAck =>
      val userActor = sender()
//      users.get(userActor).foreach(user => {
//        if (Flags.canBan(user)) {
          command.command match {
            case DesignateCommand(_, _) =>
              designate(userActor, command.userActor)
            case _ =>
          }
//        } else {
//          command.command match {
//            case _: OperableCommand => sender() ! UserError(NOT_OPERATOR)
//            case _ => super.receiveEvent(command)
//          }
//        }
        super.receiveEvent(command)
//      })
  }: Receive)
    .orElse(super.receiveEvent)

  override def add(actor: ActorRef, user: User): User = {
    val newUser =
      if (isLocal(actor) && users.isEmpty) {
        Flags.op(user)
      } else {
        user
      }

    super.add(actor, newUser)
  }

  override def rem(actor: ActorRef): Option[User] = {
    val userOpt = super.rem(actor)

    userOpt.foreach(user => {
      if (users.nonEmpty && Flags.isOp(user) && !existsOperator()) {
        val designateeActor = designatedActors.getOrElse(actor, determineNextOp)
        val designatedUser = users(designateeActor)

        val oppedUser = Flags.op(designatedUser)
        users += designateeActor -> oppedUser
        designatedActors -= actor
        designateeActor ! UserUpdated(oppedUser)
        localUsers ! UserFlags(oppedUser)
      }
    })

    userOpt
  }

  def designate(actor: ActorRef, designatee: ActorRef) = {
    users.get(actor).foreach(user => {
      val result =
        if (Flags.isOp(user)) {
          users.get(designatee).fold[ChatEvent](UserError(INVALID_USER))(designatedUser => {
            designatedActors += actor -> designatee
            UserInfo(USER_DESIGNATED(designatedUser.name))
          })
        } else {
          UserError(NOT_OPERATOR)
        }
      if (isLocal(actor)) {
        actor ! result
      }
    })
  }

  def existsOperator(): Boolean = {
    // O(n) sadface
    !users.values.forall(!Flags.isOp(_))
  }

  // First user from each server, then determine one with the oldest/lowest channel timestamp
  def determineNextOp: ActorRef = {
    val checkedAddresses = mutable.HashSet.empty[Address]

    users
      .reduceLeft[(ActorRef, User)] {
        case (nextOp, (actor, user)) =>
          if (!checkedAddresses.contains(actor.path.address)) {
            checkedAddresses += actor.path.address
            if (nextOp._2.channelTimestamp > user.channelTimestamp) {
              actor -> user
            } else {
              nextOp
            }
          } else {
            nextOp
          }
      }
      ._1
  }
}
