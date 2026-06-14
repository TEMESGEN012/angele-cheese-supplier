package com.example.data.api

import com.example.data.model.Product
import com.example.data.model.Order
import com.example.data.model.User
import retrofit2.http.*

interface ApiService {
    @GET("products")
    suspend fun getProducts(): List<Product>

    @POST("products")
    suspend fun addProduct(@Body product: Product): Product

    @PUT("products/{id}")
    suspend fun updateProduct(@Path("id") id: String, @Body product: Product): Product

    @DELETE("products/{id}")
    suspend fun deleteProduct(@Path("id") id: String)

    @GET("orders")
    suspend fun getOrders(): List<Order>

    @POST("orders")
    suspend fun placeOrder(@Body order: Order): Order

    @PUT("orders/{id}/status")
    suspend fun updateOrderStatus(
        @Path("id") id: Long,
        @Body statusUpdate: Map<String, String>
    ): Order

    @DELETE("orders/{id}")
    suspend fun deleteOrder(@Path("id") id: Long)

    @POST("users/login")
    suspend fun login(@Body credentials: Map<String, String>): User

    @POST("users/register")
    suspend fun register(@Body user: User): User
}
