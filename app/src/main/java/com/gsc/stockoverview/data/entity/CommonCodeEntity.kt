package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "common_code",
    foreignKeys = [
        ForeignKey(
            entity = CommonCodeEntity::class,
            parentColumns = ["code"],
            childColumns = ["parent_code"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parent_code"])]
)
data class CommonCodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "code")
    val code: String,                // 코드 (예: ACC001, ACC_ISA)

    @ColumnInfo(name = "parent_code")
    val parentCode: String? = null,  // 부모 코드 (루트일 경우 null)

    @ColumnInfo(name = "name")
    val name: String,                // 코드명 (예: 계좌구분, ISA)

    @ColumnInfo(name = "description")
    val description: String? = null, // 코드 설명

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,          // 정렬 순서

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,    // 사용 여부

    @ColumnInfo(name = "attr1")
    val attr1: String? = null,       // 추가 속성 1

    @ColumnInfo(name = "attr2")
    val attr2: String? = null,       // 추가 속성 2

    @ColumnInfo(name = "attr3")
    val attr3: String? = null        // 추가 속성 3
)
