package com.binarymonks.klab.actors

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor

/**
 * Exploration of working with actors in an object oriented way.
 *
 * In this example we have an Account with a balance. This object has state which we want to modify concurrently.
 *
 * There is of course the old school method of various locks and synchronizaton, but here we will use a kotlin actor.
 *
 * This means that we achieve our "thread safety" through communication via a channel.
 */


/**
 * An object oriented wrapper for communicating with the actor.
 */
class Account : CoroutineScope {
    private val job = Job()
    override val coroutineContext = Dispatchers.Unconfined + job
    private val actor = accountActor()

    suspend fun getBalance(): Double {
        val result = CompletableDeferred<Double>()
        actor.send(AccountActorMsg.GetBalance(result))
        return result.await()
    }

    suspend fun deposit(amount: Double) {
        val result = CompletableDeferred<Unit>()
        actor.send(AccountActorMsg.Deposit(amount, result))
        return result.await() // We want to do an await for the result because you need to know if operation was successful.
    }

    suspend fun withdraw(amount: Double) {
        val result = CompletableDeferred<Unit>()
        actor.send(AccountActorMsg.Withdraw(amount, result))
        return result.await() // We want to do an await for the result because you need to know if operation was successful.
    }
}

/**
 * The actor that provides thread safe communication with the balance state.
 */
private fun CoroutineScope.accountActor() = actor<AccountActorMsg> {

    // This is the state that we want to protect - keep safe from concurrent modification
    var balance = 0.0

    for (message in channel) {
        when (message) {
            is AccountActorMsg.GetBalance -> {
                message.result.complete(balance)
            }
            is AccountActorMsg.Withdraw -> {
                if (message.amount < 0) {
                    message.result.completeExceptionally(Exception("Cannot withdraw negative amount"))
                }
                balance -= message.amount
                message.result.complete(Unit)
            }
            is AccountActorMsg.Deposit -> {
                if (message.amount < 0) {
                    message.result.completeExceptionally(Exception("Cannot deposit negative amount"))
                }
                balance += message.amount
                message.result.complete(Unit)
            }
        }
    }
}

private sealed class AccountActorMsg {
    data class GetBalance(val result: CompletableDeferred<Double>) : AccountActorMsg()
    data class Deposit(val amount: Double, val result: CompletableDeferred<Unit>) : AccountActorMsg()
    data class Withdraw(val amount: Double, val result: CompletableDeferred<Unit>) : AccountActorMsg()
}

/**
 * Lets create an account and modify the balance in what I think is a highly concurrent way.
 */
fun main() = runBlocking {
    val myAccount = Account()
    var accountActions = MutableList(1_000_000) {
        GlobalScope.launch {
            myAccount.deposit(1.0)
        }
    }
    accountActions.addAll(List(1_000_000) {
        GlobalScope.launch {
            myAccount.withdraw(1.0)
        }
    })
    accountActions.forEach {
        it.join()
    }
    println(myAccount.getBalance())
}



