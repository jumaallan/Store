package com.dropbox.store.rx2.test

import com.dropbox.android.external.store4.ResponseOrigin
import com.dropbox.android.external.store4.StoreBuilder
import com.dropbox.android.external.store4.StoreRequest
import com.dropbox.android.external.store4.StoreResponse
import com.dropbox.android.external.store4.impl.SourceOfTruth
import com.dropbox.store.rx2.observe
import com.dropbox.store.rx2.fromFlowable
import com.dropbox.store.rx2.fromFlowablePersister
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subscribers.TestSubscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.atomic.AtomicInteger

@RunWith(JUnit4::class)
@FlowPreview
@ExperimentalCoroutinesApi
class RxFlowableStoreTest {
    private val testScheduler = TestScheduler()
    private val atomicInteger = AtomicInteger(0)
    private val fakeDisk = mutableMapOf<Int, String>()
    private val store =
        StoreBuilder.fromFlowable<Int, String, String> ({
            Flowable.create({ emitter ->
                emitter.onNext("$it ${atomicInteger.incrementAndGet()} occurrence")
                emitter.onNext("$it ${atomicInteger.incrementAndGet()} occurrence")
                emitter.onComplete()
            }, BackpressureStrategy.LATEST)
        },
            sourceOfTruth = SourceOfTruth.fromFlowablePersister(
                reader = {
                    if (fakeDisk[it] != null)
                        Flowable.fromCallable { fakeDisk[it]!! }
                    else
                        Flowable.empty<String>()
                },
                writer = { key, value ->
                    Single.fromCallable { fakeDisk[key] = value }
                }
            ))
            .build()

    @Test
    fun simpleTest() {
        var testSubscriber = TestSubscriber<StoreResponse<String>>()
        store.observe(StoreRequest.fresh(3))
            .subscribeOn(testScheduler)
            .subscribe(testSubscriber)
        testScheduler.triggerActions()
        testSubscriber
            .awaitCount(3)
            .assertValues(
                StoreResponse.Loading<String>(ResponseOrigin.Fetcher),
                StoreResponse.Data("3 1 occurrence", ResponseOrigin.Fetcher),
                StoreResponse.Data("3 2 occurrence", ResponseOrigin.Fetcher)
            )

        testSubscriber = TestSubscriber<StoreResponse<String>>()
        store.observe(StoreRequest.cached(3, false))
            .subscribeOn(testScheduler)
            .subscribe(testSubscriber)
        testScheduler.triggerActions()
        testSubscriber
            .awaitCount(2)
            .assertValues(
                StoreResponse.Data("3 2 occurrence", ResponseOrigin.Cache),
                StoreResponse.Data("3 2 occurrence", ResponseOrigin.Persister)
            )

        testSubscriber = TestSubscriber<StoreResponse<String>>()
        store.observe(StoreRequest.fresh(3))
            .subscribeOn(testScheduler)
            .subscribe(testSubscriber)
        testScheduler.triggerActions()
        testSubscriber
            .awaitCount(3)
            .assertValues(
                StoreResponse.Loading<String>(ResponseOrigin.Fetcher),
                StoreResponse.Data("3 3 occurrence", ResponseOrigin.Fetcher),
                StoreResponse.Data("3 4 occurrence", ResponseOrigin.Fetcher)
            )

        testSubscriber = TestSubscriber<StoreResponse<String>>()
        store.observe(StoreRequest.cached(3, false))
            .subscribeOn(testScheduler)
            .subscribe(testSubscriber)
        testScheduler.triggerActions()
        testSubscriber
            .awaitCount(2)
            .assertValues(
                StoreResponse.Data("3 4 occurrence", ResponseOrigin.Cache),
                StoreResponse.Data("3 4 occurrence", ResponseOrigin.Persister)
            )
    }
}
