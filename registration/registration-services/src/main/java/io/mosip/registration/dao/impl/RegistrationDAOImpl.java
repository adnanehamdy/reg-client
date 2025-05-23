package io.mosip.registration.dao.impl;

import static io.mosip.registration.constants.LoggerConstants.LOG_SAVE_PKT;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import io.mosip.commons.packet.dto.packet.SimpleDto;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationClientStatusCode;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationType;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.dao.RegistrationDAO;
import io.mosip.registration.dto.PacketStatusDTO;
import io.mosip.registration.dto.RegistrationDTO;
import io.mosip.registration.dto.RegistrationDataDto;
import io.mosip.registration.dto.UiSchemaDTO;
import io.mosip.registration.entity.Registration;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.exception.RegBaseUncheckedException;
import io.mosip.registration.exception.RegistrationExceptionConstants;
import io.mosip.registration.repositories.RegistrationRepository;
import io.mosip.registration.service.IdentitySchemaService;

/**
 * The implementation class of {@link RegistrationDAO}.
 *
 * @author Balaji Sridharan
 * @author Mahesh Kumar
 * @author Saravanakumar Gnanaguru
 * @since 1.0.0
 */
@Repository
@Transactional
public class RegistrationDAOImpl implements RegistrationDAO {

	/** The registration repository. */
	@Autowired
	private RegistrationRepository registrationRepository;
	
	@Autowired
	private IdentitySchemaService identitySchemaService;

	/** Object for Logger. */
	private static final Logger LOGGER = AppConfig.getLogger(RegistrationDAOImpl.class);

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.dao.RegistrationDAO#save(java.lang.String,
	 * io.mosip.registration.dto.RegistrationDTO)
	 */
	@Override
	public void save(String zipFileName, RegistrationDTO registrationDTO) throws RegBaseCheckedException {
		try {
			LOGGER.info(LOG_SAVE_PKT, APPLICATION_NAME, APPLICATION_ID, "Save Registartion has been started");

			Timestamp time = Timestamp.valueOf(DateUtils.getUTCCurrentDateTime());

			Registration registration = new Registration();
			registration.setId(registrationDTO.getRegistrationId());
			registration.setRegType(RegistrationType.NEW.getCode());
			registration.setStatusCode(registrationDTO.getRegistrationMetaDataDTO().getRegistrationCategory());
			registration.setLangCode(RegistrationConstants.ENGLISH_LANG_CODE);
			registration.setStatusTimestamp(time);
			registration.setAckFilename(zipFileName + "_Ack." + RegistrationConstants.ACKNOWLEDGEMENT_FORMAT);
			registration.setClientStatusCode(RegistrationClientStatusCode.CREATED.getCode());
			registration.setUploadCount((short) 0);
			registration.setRegCntrId(
					SessionContext.userContext().getRegistrationCenterDetailDTO().getRegistrationCenterId());
			registration.setIsActive(true);
			registration.setCrBy(SessionContext.userContext().getUserId());
			registration.setCrDtime(time);
			registration.setRegUsrId(SessionContext.userContext().getUserId());
			registration.setApproverUsrId(SessionContext.userContext().getUserId());
			registration.setPreRegId(registrationDTO.getPreRegistrationId());
			
			RegistrationDataDto registrationDataDto = new RegistrationDataDto();
			
			List<String> fullName = new ArrayList<>();
			String fullNameKey = getKey(RegistrationConstants.UI_SCHEMA_SUBTYPE_FULL_NAME);
			if(fullNameKey != null) {
				List<String> fullNameKeys = Arrays.asList(fullNameKey.split(RegistrationConstants.COMMA));
				for (String key : fullNameKeys) {
					Object fullNameObj = registrationDTO.getDemographics().get(key);
					fullName.add(getAdditionalInfo(fullNameObj));
				}
			}

			Object emailObj = registrationDTO.getDemographics().get(getKey(RegistrationConstants.UI_SCHEMA_SUBTYPE_EMAIL));
			Object phoneObj = registrationDTO.getDemographics().get(getKey(RegistrationConstants.UI_SCHEMA_SUBTYPE_PHONE));
			
			fullName.removeIf(Objects::isNull);
			registrationDataDto.setName(String.join(RegistrationConstants.SPACE, fullName));
			registrationDataDto.setEmail(getAdditionalInfo(emailObj));
			registrationDataDto.setPhone(getAdditionalInfo(phoneObj));
			
			String additionalInfo = JsonUtils.javaObjectToJsonString(registrationDataDto);
			registration.setAdditionalInfo(additionalInfo.getBytes());

			registrationRepository.create(registration);

			LOGGER.info(LOG_SAVE_PKT, APPLICATION_NAME, APPLICATION_ID, "Save Registration has been ended");
		} catch (RuntimeException | JsonProcessingException runtimeException) {
			throw new RegBaseUncheckedException(
					RegistrationExceptionConstants.REG_PACKET_SAVE_TO_DB_EXCEPTION.getErrorCode(),
					RegistrationExceptionConstants.REG_PACKET_SAVE_TO_DB_EXCEPTION.getErrorMessage(), runtimeException);
		}
	}
	
	private String getKey(String subType) throws RegBaseCheckedException {
		String key = null;
		List<UiSchemaDTO> schemaFields = identitySchemaService.getLatestEffectiveUISchema();
		for (UiSchemaDTO schemaField : schemaFields) {
			if (schemaField.getSubType() != null && schemaField.getSubType().equalsIgnoreCase(subType)) {

				if (subType.equalsIgnoreCase(RegistrationConstants.UI_SCHEMA_SUBTYPE_FULL_NAME)) {
					key = key == null ? schemaField.getId() : key.concat(RegistrationConstants.COMMA).concat(schemaField.getId());
				} else {
					key = schemaField.getId();
					return key;
				}
			}
		}
		return key;
	}

	private String getAdditionalInfo(Object fieldValue) {
		if(fieldValue == null) { return null; }

		if (fieldValue instanceof List<?>) {
			Optional<SimpleDto> demoValueInRequiredLang = ((List<SimpleDto>) fieldValue).stream()
					.filter(valueDTO -> valueDTO.getLanguage().equals(ApplicationContext.applicationLanguage())).findFirst();

			if (demoValueInRequiredLang.isPresent()) {
				return demoValueInRequiredLang.get().getValue();
			}
		}

		if (fieldValue instanceof String) {
			return (String) fieldValue;
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mosip.registration.dao.RegistrationDAO#updateStatus(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public Registration updateRegistration(String registrationID, String statusComments, String clientStatusCode) {
		try {
			LOGGER.info("REGISTRATION - UPDATE_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
					"Packet updation has been started");

			Timestamp timestamp = Timestamp.valueOf(DateUtils.getUTCCurrentDateTime());
			Registration registration = registrationRepository.getOne(registrationID);
			// registration.setStatusCode(clientStatusCode);
			registration.setStatusTimestamp(timestamp);
			registration.setClientStatusCode(clientStatusCode);
			registration.setClientStatusTimestamp(timestamp);
			registration.setClientStatusComments(statusComments);
			registration.setApproverUsrId(SessionContext.userContext().getUserId());
			registration.setApproverRoleCode(SessionContext.userContext().getRoles().get(0));
			registration.setUpdBy(SessionContext.userContext().getUserId());
			registration.setUpdDtimes(timestamp);

			LOGGER.info("REGISTRATION - UPDATE_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
					"Packet updation has been ended");

			return registrationRepository.update(registration);
		} catch (RuntimeException runtimeException) {
			throw new RegBaseUncheckedException(RegistrationConstants.PACKET_UPDATE_STATUS,
					runtimeException.toString());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mosip.registration.dao.RegistrationDAO#getEnrollmentByStatus(java.lang.
	 * String)
	 */
	@Override
	public List<Registration> getEnrollmentByStatus(String status) {
		LOGGER.info("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Retrieving packets based on status");

		return registrationRepository.findByclientStatusCodeOrderByCrDtime(status);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.registration.dao.RegistrationDAO#getPacketsToBeSynched(java.util.
	 * List)
	 */
	public List<Registration> getPacketsToBeSynched(List<String> statusCodes) {
		return registrationRepository.findByClientStatusCodeInOrderByUpdDtimesDesc(statusCodes);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.registration.dao.RegistrationDAO#getRegistrationByStatus(java.util.
	 * List)
	 */
	@Override
	public List<Registration> getRegistrationByStatus(List<String> packetStatus) {
		LOGGER.info("REGISTRATION - GET_PACKET_DETAILS_BY_ID - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"got the packet details by id");

		return registrationRepository.findByStatusCodes(packetStatus.get(0), packetStatus.get(1), packetStatus.get(2),
				packetStatus.get(3));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.mosip.registration.dao.RegistrationDAO#updateRegStatus(java.lang.String)
	 */
	public Registration updateRegStatus(PacketStatusDTO registrationPacket) {
		LOGGER.info("REGISTRATION - UPDATE_THE_PACKET_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Updating the packet details in the Registration table");

		Timestamp timestamp = Timestamp.valueOf(DateUtils.getUTCCurrentDateTime());

		Registration reg = registrationRepository.getOne(registrationPacket.getFileName());
		reg.setClientStatusCode(registrationPacket.getPacketClientStatus());
		if (registrationPacket.getUploadStatus() != null) {
			reg.setFileUploadStatus(registrationPacket.getUploadStatus());
		}
		reg.setIsActive(true);
		reg.setUploadTimestamp(timestamp);
		reg.setClientStatusTimestamp(timestamp);
		reg.setClientStatusComments(registrationPacket.getClientStatusComments());
		reg.setUpdDtimes(timestamp);
		reg.setUploadCount((short) (reg.getUploadCount() + 1));
		reg.setUpdBy(SessionContext.userContext().getUserId());
		reg.setServerStatusCode(registrationPacket.getPacketServerStatus());
		return registrationRepository.update(reg);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.registration.dao.RegistrationDAO#updatePacketSyncStatus(io.mosip.
	 * registration.entity.Registration)
	 */
	public Registration updatePacketSyncStatus(PacketStatusDTO packet) {
		LOGGER.info("REGISTRATION - UPDATE_THE_PACKET_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Updating the packet details in the Registration table");

		Timestamp timestamp = Timestamp.valueOf(DateUtils.getUTCCurrentDateTime());
		Registration reg = registrationRepository.getOne(packet.getFileName());
		// reg.setStatusCode(packet.getPacketClientStatus());
		reg.setClientStatusCode(packet.getPacketClientStatus());
		reg.setIsActive(true);
		reg.setUploadTimestamp(timestamp);
		return registrationRepository.update(reg);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * io.mosip.registration.dao.RegistrationDAO#getAllReRegistrationPackets(java.
	 * lang.String[])
	 */
	public List<Registration> getAllReRegistrationPackets(String[] status) {
		return registrationRepository.findByClientStatusCodeAndServerStatusCode(status[0], status[1]);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.dao.RegistrationDAO#getRegistrationById(java.lang.
	 * String)
	 */
	@Override
	public Registration getRegistrationById(String clientStatusCode, String rId) {
		LOGGER.debug("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Get Registration based on reg Id and client status code started");
		return registrationRepository.findByClientStatusCodeAndId(clientStatusCode, rId);
	}

	@Override
	public List<Registration> get(List<String> regIds) {
		LOGGER.debug("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Get Registrations based on reg Ids started");

		Iterable<String> iterableRegIds = regIds;

		LOGGER.debug("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Get Registration based on reg Ids completed");

		return registrationRepository.findAllById(iterableRegIds);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.dao.RegistrationDAO#get(java.sql.Timestamp,
	 * java.lang.String)
	 */
	@Override
	public List<Registration> get(Timestamp crDtimes, String serverStatusCode) {

		LOGGER.debug("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Retrieving Registrations based on crDtime and status");

		return registrationRepository.findByCrDtimeBeforeAndServerStatusCode(crDtimes, serverStatusCode);

	}

	@Override
	public List<Registration> findByServerStatusCodeIn(List<String> serverStatusCodes) {

		LOGGER.debug("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Retrieving Registrations based on server status codes");

		return registrationRepository.findByServerStatusCodeIn(serverStatusCodes);

	}

	@Override
	public List<Registration> findByServerStatusCodeNotIn(List<String> serverStatusCodes) {

		LOGGER.debug("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Retrieving Registrations based on server status codes");

		return registrationRepository.findByServerStatusCodeNotInOrServerStatusCodeIsNull(serverStatusCodes);

	}

	public List<Registration> fetchPacketsToUpload(List<String> clientStatus, String serverStatus) {

		LOGGER.debug("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"Retrieving Registrations based on client and server status codes");

		return registrationRepository.findByClientStatusCodeInOrServerStatusCodeOrderByUpdDtimesDesc(clientStatus,
				serverStatus);
	}
	
	@Override
	public List<Registration> fetchReRegisterPendingPackets() {

		LOGGER.debug("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"fetchReRegisterPendingPackets -Retrieving Registrations based on client and server status codes");

		return registrationRepository.findByClientStatusCodeNotInAndServerStatusCodeIn(
				Arrays.asList(RegistrationClientStatusCode.RE_REGISTER.getCode()),
				Arrays.asList(RegistrationConstants.PACKET_STATUS_CODE_REREGISTER));
	}
	
	@Override
	public List<Registration> getAllRegistrations() {
		LOGGER.debug("REGISTRATION - BY_STATUS - REGISTRATION_DAO", APPLICATION_NAME, APPLICATION_ID,
				"fetch all the registration entries");
		
		return registrationRepository.findAll();
	}
}
