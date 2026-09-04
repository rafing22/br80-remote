package com.br80.remote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ButtonMappingDao {

    // Caricamento in blocco all'avvio, per popolare la cache in memoria di MappingStorage:
    // le letture successive (una per pressione fisica) non toccano più il database.
    @Query("SELECT * FROM button_mappings")
    fun getAll(): List<ButtonMappingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: ButtonMappingEntity)

    @Query("DELETE FROM button_mappings WHERE profileName = :profileName")
    fun deleteProfile(profileName: String)

    @Query("UPDATE button_mappings SET profileName = :newName WHERE profileName = :oldName")
    fun renameProfile(oldName: String, newName: String)
}
