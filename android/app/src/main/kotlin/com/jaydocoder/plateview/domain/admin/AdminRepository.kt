package com.jaydocoder.plateview.domain.admin

interface AdminRepository {
    suspend fun getVehicleCreationCapabilities(accessToken: String): VehicleCreationCapabilities

    suspend fun listVehicles(
        accessToken: String,
        keyword: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): ManagedVehiclePage
    suspend fun getVehicle(accessToken: String, vehicleId: Long): ManagedVehicle
    suspend fun createVehicle(accessToken: String, command: VehicleWriteCommand): ManagedVehicle
    suspend fun updateVehicle(accessToken: String, vehicleId: Long, version: Int, command: VehicleWriteCommand): ManagedVehicle
    suspend fun deactivateVehicle(accessToken: String, vehicleId: Long, version: Int): ManagedVehicle

    suspend fun listUsers(accessToken: String): List<ManagedUser>
    suspend fun createUser(accessToken: String, command: UserCreateCommand): ManagedUser
    suspend fun updateUser(accessToken: String, userId: Long, version: Int, command: UserUpdateCommand): ManagedUser

    suspend fun listImportBatches(accessToken: String): List<ManagedImportBatchSummary>
    suspend fun getImportBatch(
        accessToken: String,
        batchId: Long,
        limit: Int = 100,
        offset: Int = 0,
        filter: ImportRowFilter = ImportRowFilter.REVIEW,
    ): ManagedImportBatch
    suspend fun getImportRowDetail(accessToken: String, batchId: Long, rowId: Long): ManagedImportRowDetail
    suspend fun previewImport(accessToken: String, fileName: String, content: ByteArray): ManagedImportBatch
    suspend fun updateImportResolution(accessToken: String, batchId: Long, rowId: Long, resolution: String): ManagedImportBatch
    suspend fun publishImport(accessToken: String, batchId: Long): ManagedImportBatch
    suspend fun rollbackImport(accessToken: String, batchId: Long): ManagedImportBatch

    suspend fun listAuditEntries(
        accessToken: String,
        filter: AuditFilter = AuditFilter(),
        limit: Int = 50,
        offset: Int = 0,
    ): ManagedAuditPage
}
