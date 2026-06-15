package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "price_drop_alerts"
    private const val CHANNEL_NAME = "Price Drop Alerts"
    private const val CHANNEL_DESC = "Notifications for tracked product price drops"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendPriceDropNotification(
        context: Context,
        productId: Int,
        title: String,
        merchant: String,
        price: Double,
        previousPrice: Double
    ) {
        // Intent to launch MainActivity when notification clicked
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("PRODUCT_ID", productId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            productId,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val formattedPrice = String.format("%,.2f", price)
        val formattedPrev = String.format("%,.2f", previousPrice)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // fallback standard icon
            .setContentTitle("🔥 LOWEST PRICE EVER! ($merchant)")
            .setContentText("Price dropped for $title! Now ₹$formattedPrice (was ₹$formattedPrev)")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$title from $merchant dropped from ₹$formattedPrev to its lowest ever: ₹$formattedPrice! Tap to view price trends.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_PROMO)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(productId, notificationBuilder.build())
        } catch (e: SecurityException) {
            // Permission not granted on Android 13+; handled gracefully in UI
        }
    }
}
