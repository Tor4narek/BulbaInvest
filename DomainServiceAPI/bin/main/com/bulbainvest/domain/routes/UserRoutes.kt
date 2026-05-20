package com.bulbainvest.domain.routes

import com.bulbainvest.domain.api.UpdateUserRequest
import com.bulbainvest.domain.api.toUserDto
import com.bulbainvest.domain.plugins.USER_AUTH
import com.bulbainvest.domain.plugins.userId
import com.bulbainvest.domain.service.UserService
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.route

fun Route.userRoutes(users: UserService) {
    authenticate(USER_AUTH) {
        route("/api/users/me") {
            get {
                val row = users.getById(call.userId())
                call.respond(row.toUserDto())
            }
            patch {
                val body = call.receive<UpdateUserRequest>()
                body.name?.let { users.updateName(call.userId(), it.trim()) }
                val row = users.getById(call.userId())
                call.respond(row.toUserDto())
            }
        }
    }
}
