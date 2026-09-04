package com.br80.remote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ButtonMappingEntity::class], version = 1, exportSchema = false)
abstract class Br80Database : RoomDatabase() {

    abstract fun buttonMappingDao(): ButtonMappingDao

    companion object {
        @Volatile
        private var instance: Br80Database? = null

        fun getInstance(context: Context): Br80Database {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    Br80Database::class.java,
                    "br80_mappings.db"
                )
                    // Tabella piccola (decine di righe: 28 combinazioni x N profili), letta
                    // per intero una sola volta all'avvio in una cache in memoria (vedi
                    // MappingStorage): il costo di una query bloccante sul thread principale
                    // qui è trascurabile, non giustifica l'overhead di coroutine/executor.
                    .allowMainThreadQueries()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
