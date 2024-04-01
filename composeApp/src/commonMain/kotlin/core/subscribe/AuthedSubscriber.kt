package io.github.vrcmteam.vrcm.core.subscribe

object AuthCentre  {

   private val Subscribers = mutableListOf<(String?,String?) -> Unit>()

    object Subscriber  {


     fun onAuthed(callback: (String?,String?) -> Unit) {
        Subscribers.add(callback)
     }

  }

    object Publisher  {

      fun sendAuthed(userId: String?,userName: String?) {
          Subscribers.forEach {
              it.invoke(userId,userName)
          }
      }
  }
}