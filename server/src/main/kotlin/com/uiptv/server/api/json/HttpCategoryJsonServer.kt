package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.service.AccountService
import com.uiptv.service.CategoryResolver
import com.uiptv.service.CategoryService
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils
import com.uiptv.util.StringUtils.isNotBlank
import java.io.IOException

class HttpCategoryJsonServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val account = AccountService.getInstance().getById(getParam(ex, "accountId"))
        if (account == null) {
            generateJsonResponse(ex, "[]")
            return
        }
        applyMode(account, getParam(ex, "mode"))
        val categories: List<Category> = CategoryService.getInstance().get(account)
        val resolved = CategoryResolver().resolveCategories(account, categories)
        generateJsonResponse(ex, StringUtils.EMPTY + com.uiptv.util.ServerUtils.objectToJson(resolved))
    }

    private fun applyMode(account: Account?, mode: String?) {
        if (account == null || !isNotBlank(mode)) {
            return
        }
        try {
            account.action = Account.AccountAction.valueOf(mode!!.lowercase())
        } catch (_: Exception) {
            account.action = Account.AccountAction.itv
        }
    }
}
