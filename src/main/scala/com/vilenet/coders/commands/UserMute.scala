package com.vilenet.coders.commands

/**
  * Created by filip on 1/9/17.
  */
case class UserMute(override val toUsername: String) extends UserToChannelCommand
