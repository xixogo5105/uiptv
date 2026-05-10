package com.uiptv.db

import org.jetbrains.exposed.sql.transactions.transaction

interface CrudRepository<ID, T> {
    fun findAll(): List<T>

    fun findById(id: ID): T?

    fun save(entity: T): T

    fun deleteById(id: ID)
}

abstract class ExposedCrudRepository<ID, T>(
) : CrudRepository<ID, T> {
    protected fun <R> query(block: () -> R): R = transaction(SqlConnectionRuntime.database()) { block() }
}
