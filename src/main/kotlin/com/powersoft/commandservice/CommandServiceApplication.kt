package com.powersoft.commandservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CommandServiceApplication

fun main(args: Array<String>) {
    runApplication<CommandServiceApplication>(*args)
}