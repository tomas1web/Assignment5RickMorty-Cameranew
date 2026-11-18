package com.example.assignment3_rickmortyexplorer

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Item guardado na base de dados (TP4)
data class SavedItem(
    val name: String = "",
    val imageUrl: String? = null,
    val category: String = "",
    val label1: String = "",
    val value1: String = "",
    val label2: String = "",
    val value2: String = "",
    val savedAt: Long = 0L
)

fun SavedItem.toDisplayItem() = DisplayItem(
    name = name, imageUrl = imageUrl,
    label1 = label1, value1 = value1,
    label2 = label2, value2 = value2
)

// TP5: foto tirada a uma localização, com coordenadas
data class LocationPhoto(
    val locationName: String = "",
    val imageBase64: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: Long = 0L
)

class FirebaseRepository {

    private val root by lazy { FirebaseDatabase.getInstance().reference }
    private val rollCountRef by lazy { root.child("rollCount") }
    private val lastSavedRef by lazy { root.child("lastSaved") }
    private val historyRef by lazy { root.child("history") }
    private val photosRef by lazy { root.child("locationPhotos") }

    // As chaves do Firebase não podem ter . # $ [ ] /
    private fun photoKey(locationName: String): String =
        locationName.map { if (it in ".#\$[]/") '_' else it }.joinToString("")

    fun incrementRollCount() {
        rollCountRef.setValue(ServerValue.increment(1L))
    }

    fun rollCountFlow(): Flow<Int> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend((snapshot.value as? Long)?.toInt() ?: 0)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        rollCountRef.addValueEventListener(listener)
        awaitClose { rollCountRef.removeEventListener(listener) }
    }

    fun historyFlow(): Flow<List<SavedItem>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = snapshot.children.mapNotNull { it.getValue(SavedItem::class.java) }
                trySend(list.sortedByDescending { it.savedAt })
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        historyRef.addValueEventListener(listener)
        awaitClose { historyRef.removeEventListener(listener) }
    }

    fun saveItem(item: SavedItem, onResult: (Boolean) -> Unit) {
        val toSave = item.copy(savedAt = System.currentTimeMillis())
        lastSavedRef.setValue(toSave)
        historyRef.push().setValue(toSave)
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun loadLast(onResult: (SavedItem?) -> Unit, onError: () -> Unit) {
        lastSavedRef.get()
            .addOnSuccessListener { onResult(it.getValue(SavedItem::class.java)) }
            .addOnFailureListener { onError() }
    }

    // TP5: guarda (ou substitui) a foto de uma localização
    fun saveLocationPhoto(photo: LocationPhoto, onResult: (Boolean) -> Unit) {
        photosRef.child(photoKey(photo.locationName)).setValue(photo)
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    // TP5: lê a foto guardada de uma localização (null se não existe)
    fun loadLocationPhoto(locationName: String, onResult: (LocationPhoto?) -> Unit) {
        photosRef.child(photoKey(locationName)).get()
            .addOnSuccessListener { onResult(it.getValue(LocationPhoto::class.java)) }
            .addOnFailureListener { onResult(null) }
    }
}