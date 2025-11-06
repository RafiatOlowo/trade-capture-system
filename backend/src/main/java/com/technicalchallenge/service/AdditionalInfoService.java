package com.technicalchallenge.service;

import com.technicalchallenge.constants.AdditionalInfoConstants;
import com.technicalchallenge.dto.AdditionalInfoDTO;
import com.technicalchallenge.model.AdditionalInfo;
import com.technicalchallenge.repository.AdditionalInfoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
@Transactional
public class AdditionalInfoService {

    @Autowired
    private AdditionalInfoRepository additionalInfoRepository;

    @Autowired
    private ModelMapper modelMapper;

    public List<AdditionalInfoDTO> getAdditionalInfoForEntity(String entityType, Long entityId) {
        List<AdditionalInfo> additionalInfoList = additionalInfoRepository.findActiveByEntityTypeAndEntityId(entityType, entityId);
        return additionalInfoList.stream()
                .map(info -> modelMapper.map(info, AdditionalInfoDTO.class))
                .collect(Collectors.toList());
    }

    public AdditionalInfoDTO addAdditionalInfo(AdditionalInfoDTO dto) {
        // Check if field already exists and deactivate old version
        AdditionalInfo existing = additionalInfoRepository.findActiveByEntityTypeAndEntityIdAndFieldName(
                dto.getEntityType(), dto.getEntityId(), dto.getFieldName());

        if (existing != null) {
            existing.setActive(false);
            existing.setDeactivatedDate(LocalDateTime.now());
            additionalInfoRepository.save(existing);
        }

        // Create new version
        AdditionalInfo newInfo = modelMapper.map(dto, AdditionalInfo.class);
        newInfo.setId(null); // Ensure new record
        newInfo.setActive(true);
        newInfo.setCreatedDate(LocalDateTime.now());
        newInfo.setLastModifiedDate(LocalDateTime.now());
        newInfo.setVersion(existing != null ? existing.getVersion() + 1 : 1);

        AdditionalInfo saved = additionalInfoRepository.save(newInfo);
        return modelMapper.map(saved, AdditionalInfoDTO.class);
    }

    public void removeAdditionalInfo(String entityType, Long entityId, String fieldName) {
        AdditionalInfo existing = additionalInfoRepository.findActiveByEntityTypeAndEntityIdAndFieldName(
                entityType, entityId, fieldName);

        if (existing != null) {
            existing.setActive(false);
            existing.setDeactivatedDate(LocalDateTime.now());
            additionalInfoRepository.save(existing);
        }
    }

    public AdditionalInfoDTO updateAdditionalInfo(AdditionalInfoDTO dto) {
        return addAdditionalInfo(dto); // Same logic as add - version control
    }

    // ------------------------------------------------------------------------
    // METHODS FOR SETTLEMENT INSTRUCTIONS INTEGRATION
    // ------------------------------------------------------------------------

    /**
     * Saves or updates the settlement instruction record linked to a Trade,
     * * @param tradeId The ID of the parent Trade entity.
     * @param instructions The raw settlement instruction text.
     */
    public void saveSettlementInstructions(Long tradeId, String instructions) {
        if (tradeId == null || instructions == null || instructions.isBlank()) {
            return;
        }

        // Create a DTO wrapper for the instructions
        AdditionalInfoDTO siDto = new AdditionalInfoDTO();
        siDto.setEntityType(AdditionalInfoConstants.ENTITY_TYPE_TRADE);
        siDto.setEntityId(tradeId);
        siDto.setFieldName(AdditionalInfoConstants.FIELD_NAME_SETTLEMENT_INSTRUCTIONS);
        siDto.setFieldValue(instructions);
        siDto.setFieldType(AdditionalInfoConstants.FIELD_TYPE_STRING);
        
        // Use the existing version-control logic to save/update the record
        // This will deactivate any old record and create a new one.
        this.addAdditionalInfo(siDto); 
    }

    // Retrieves the active settlement instructions for a given Trade ID.
    public String getSettlementInstructions(Long tradeId) {
        AdditionalInfo info = additionalInfoRepository.findActiveByEntityTypeAndEntityIdAndFieldName(
            AdditionalInfoConstants.ENTITY_TYPE_TRADE,
            tradeId,
            AdditionalInfoConstants.FIELD_NAME_SETTLEMENT_INSTRUCTIONS
        );
        return info != null ? info.getFieldValue() : null;
    }

    /**
     * Retrieves the instruction text for a specific Trade entity PK, regardless of the SI record's 'active' status.
     * This is used by TradeService to retrieve the SI text from the *previous* trade version (which is now inactive).
     * @param entityId The Primary Key (id) of the Trade entity version (PK-A).
     * @return The settlement instruction text, or null if not found.
     */
    public String getSettlementInstructionTextByEntityPK(Long entityId) {
        // Need the *last* SI linked to this specific PK (PK-A), as the SI's 'active' flag might have been
        // cleared when the trade version was deactivated. Then look for the latest created record.
        Optional<AdditionalInfo> infoOpt = additionalInfoRepository
            .findFirstByEntityTypeAndEntityIdAndFieldNameOrderByCreatedDateDesc(
                AdditionalInfoConstants.ENTITY_TYPE_TRADE,
                entityId,
                AdditionalInfoConstants.FIELD_NAME_SETTLEMENT_INSTRUCTIONS
            );
        
        return infoOpt.map(AdditionalInfo::getFieldValue).orElse(null);
    }
}
