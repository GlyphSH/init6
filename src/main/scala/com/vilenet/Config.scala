package com.vilenet

import java.io.File

import com.typesafe.config.ConfigFactory
import com.vilenet.coders.commands.Command

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Created by filip on 12/17/15.
  */
case object ReloadConfig extends Command

object Config {

  private var config: Config = reload()

  def apply() = {
    this.synchronized {
      config
    }
  }

  def reload() = {
    this.synchronized {
      config = new Config(sys.props("config"))
      config
    }
  }

  def load(filePath: String) = {
    val file = new File(filePath)
    if (file.exists()) {
      ConfigFactory.parseFile(file).resolve()
    } else {
      ConfigFactory.load(filePath)
    }
  }
}

class Config(filePath: String) {

  val root = Config.load(filePath)
    .getConfig(Constants.VILE_NET)

  object Server {

    val p = root.getConfig("server")

    val name = p.getString("name")
    val host = p.getString("host")
    val port = p.getInt("port")

    val akka_host = Try(p.getString("akka_host")).getOrElse(host)
    val akka_port = p.getInt("akka_port")
    val nodes = p.getStringList("nodes").asScala
      .filterNot(_ == s"$akka_host:$akka_port")

    object Registry {
      val pA = p.getConfig("registry")

      val pingDelay = pA.getInt("ping-delay")
      val dropAfter = pA.getInt("drop-after")
    }
  }

  object Accounts {

    val p = root.getConfig("accounts")

    val allowedCharacters =
      s"abcdefghijklmnopqrstuvwxyz0123456789${p.getString("allowed-illegal-characters")}".toSet

    val minLength = p.getInt("min-length")
    val maxLength = p.getInt("max-length")

    val enableMultipleLogins = p.getBoolean("enable-multiple")

    val enableIpWhitelist = p.getBoolean("enable-ip-whitelist")
    val ipWhitelist = p.getStringList("ip-whitelist").asScala.toSet
  }

  object Database {

    val p = root.getConfig("database")

    val host = p.getString("host")
    val port = p.getInt("port")
    val username = p.getString("username")
    val password = p.getString("password")

    val batchUpdateInterval = p.getInt("batch-update-interval")
  }

  object AntiFlood {

    val p = root.getConfig("anti-flood")

    val enabled = p.getBoolean("enabled")
    val maxCredits = p.getInt("max-credits")
    val packetMinCost = p.getInt("packet-min-cost")
    val packetMaxCost = p.getInt("packet-max-cost")
    val costPerByte = p.getInt("cost-per-byte")
    val creditsReturnedPerSecond = p.getInt("credits-returned-per-second")
  }

  val motd = root.getStringList("motd")
    .asScala
    .map(line => {
      line
        .replaceAll("\\$buildNumber", BuildInfo.BUILD_NUMBER)
        .replaceAll("\\$buildHash", BuildInfo.BUILD_HASH)
    })
    .toArray
}
