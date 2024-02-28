package io.github.vrcmteam.vrcm.data.dao

import coil3.PlatformContext


expect class AccountDao(context: PlatformContext) {

     fun saveAccount(username: String, password: String)

     fun accountPair():Pair<String, String>

     fun clearAccount()

}
