package com.example.adhigam.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.adhigam.data.AppContainer
import com.example.adhigam.data.model.Assignment
import com.example.adhigam.data.model.BusTracking

// Opens a link in whatever app handles it (browser, PDF viewer, maps). False when no app can.
fun Context.openExternal(uri: Uri): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
}

// Assignment files are plain /static files on the school server, like the web's Download link.
fun Context.openAssignmentFile(assignment: Assignment): Boolean =
    assignment.fileUrl.isNotBlank() && openExternal(Uri.parse(AppContainer.apiClient.fileUrl(assignment.fileUrl)))

// The bus's last reported position in the phone's maps app (the web bus portal shows the same point on a map).
fun Context.openBusLocation(bus: BusTracking): Boolean {
    val lat = bus.latitude ?: return false
    val lng = bus.longitude ?: return false
    val label = Uri.encode(bus.busName.ifBlank { "School bus" })
    return openExternal(Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")) ||
        openExternal(Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng"))
}
