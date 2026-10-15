package com.casetracker.data.mapper

import com.casetracker.data.local.entity.CaseEntity
import com.casetracker.data.local.entity.CaseEventEntity
import com.casetracker.domain.model.AccidentProcedure
import com.casetracker.domain.model.AccidentType
import com.casetracker.domain.model.Case
import com.casetracker.domain.model.CaseEvent
import com.casetracker.domain.model.CaseStatus
import com.casetracker.domain.model.CaseType

fun CaseEntity.toDomain(): Case = Case(
    id = id,
    caseNumber = caseNumber,
    caseType = CaseType.valueOf(caseType),
    title = title,
    status = CaseStatus.valueOf(status),
    progress = progress,
    parties = if (parties.isBlank()) emptyList() else parties.split(",").map { it.trim() },
    description = description,
    location = location,
    deadline = deadline,
    createdAt = createdAt,
    updatedAt = updatedAt,
    accidentDate = accidentDate,
    accidentTime = accidentTime,
    accidentType = AccidentType.valueOf(accidentType),
    procedure = AccidentProcedure.valueOf(procedure),
    handlerUnit = handlerUnit,
    hitAndRun = hitAndRun,
    appraisalExtended = appraisalExtended,
    handlingOfficer = handlingOfficer,
    selfParty = selfParty,
    otherParty = otherParty
)

fun Case.toEntity(now: Long): CaseEntity = CaseEntity(
    id = id,
    caseNumber = caseNumber,
    caseType = caseType.name,
    title = title,
    status = status.name,
    progress = progress,
    parties = parties.joinToString(","),
    description = description,
    location = location,
    deadline = deadline,
    createdAt = if (createdAt == 0L) now else createdAt,
    updatedAt = now,
    accidentDate = accidentDate,
    accidentTime = accidentTime,
    accidentType = accidentType.name,
    procedure = procedure.name,
    handlerUnit = handlerUnit,
    hitAndRun = hitAndRun,
    appraisalExtended = appraisalExtended,
    handlingOfficer = handlingOfficer,
    selfParty = selfParty,
    otherParty = otherParty
)

fun CaseEventEntity.toDomain(): CaseEvent = CaseEvent(
    id = id,
    caseId = caseId,
    eventType = eventType,
    title = title,
    description = description,
    occurredAt = occurredAt,
    isMilestone = isMilestone,
    nodeCode = nodeCode,
    expectedDate = expectedDate,
    isAuto = isAuto,
    isRequired = isRequired
)

fun CaseEvent.toEntity(caseId: Long, now: Long): CaseEventEntity = CaseEventEntity(
    id = id,
    caseId = caseId,
    eventType = eventType,
    title = title,
    description = description,
    occurredAt = occurredAt,
    isMilestone = isMilestone,
    createdAt = now,
    nodeCode = nodeCode,
    expectedDate = expectedDate,
    isAuto = isAuto,
    isRequired = isRequired
)
