package com.init6.connection.realm

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import com.init6.coders.realm.packets.{McpCharCreate, McpCharList2, McpStartup, Packets}
import com.init6.coders.realm.packets.McpStartup.RESULT_SUCCESS
import com.init6.connection.{ConnectionInfo, Init6KeepAliveActor, WriteOut}
import com.init6.db.RealmReadCookieResponse

sealed trait RealmState
case object ExpectingStartup extends RealmState
case object ExpectingLogon extends RealmState

case object ExpectingRealmCookieReadFromDAO extends RealmState

case class RealmPacket(packetId: Byte, packet: ByteString)

object RealmMessageHandler {
  def apply(connectionInfo: ConnectionInfo): Props = Props(classOf[RealmMessageHandler], connectionInfo)
}

class RealmMessageHandler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[RealmState, ActorRef] {

  var userId: Long = _

  startWith(ExpectingStartup, ActorRef.noSender)
  context.watch(connectionInfo.actor)

  when (ExpectingStartup) {
    case Event(RealmPacket(id, data), _) =>
      id match {
        case Packets.MCP_STARTUP =>
          log.info(">> Received MCP_STARTUP")
          data match {
            case McpStartup(packet) =>
<<<<<<< HEAD
              log.info(">> Retrieving realm cookie")
              goto(ExpectingRealmCookieReadFromDAO)
//              send(McpStartup(RESULT_SUCCESS))
//              log.info("<< Sent MCP_STARTUP")
//              goto(ExpectingLogon)
=======
              send(McpStartup(RESULT_SUCCESS))
              log.info("<< Sent MCP_STARTUP")
              goto(ExpectingLogon)
>>>>>>> realm
          }
        case _ =>
          log.info(">> Received MCP packet {}", id)
          stay()
      }
    case x =>
      log.info(">> Received {}", x.toString)
      stay()
  }

  when (ExpectingRealmCookieReadFromDAO) {
<<<<<<< HEAD
    case Event(RealmReadCookieResponse(userId), _) =>
      send(McpStartup(RESULT_SUCCESS))
      log.info("<< Sent MCP_STARTUP")
      goto(ExpectingLogon)
=======

>>>>>>> realm
  }

  when (ExpectingLogon) {
    case Event(RealmPacket(id, data), _) =>
      id match {
        case Packets.MCP_CHARLIST2 =>
          log.info(">> Received MCP_CHARLIST2")
          send(McpCharList2())
          log.info("<< Sent MCP_CHARLIST2")
          stay()
        case Packets.MCP_CHARCREATE =>
          log.info(">> Received MCP_CHARCREATE")
          data match {
            case McpCharCreate(packet) =>
//              daoActor ! RealmCreateCharacter()
              stay()
            case _ => stop()
          }
//          send(McpCharCreate())
//          log.info("<< Sent MCP_CHARCREATE")
//          stay()
        case _ =>
          log.info(">> Received MCP packet {}", id)
          stay()
      }
    case x =>
      log.info(">> Received {}", x.toString)
      stay()
  }

  def send(data: ByteString): Unit = {
    connectionInfo.actor ! WriteOut(data)
  }
}