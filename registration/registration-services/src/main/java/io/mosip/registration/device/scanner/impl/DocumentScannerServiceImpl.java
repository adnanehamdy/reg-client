package io.mosip.registration.device.scanner.impl;

import static io.mosip.registration.constants.LoggerConstants.LOG_REG_DOC_SCAN_CONTROLLER;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.imageio.ImageIO;

import eu.gnome.morena.*;
import io.mosip.registration.exception.RegBaseCheckedException;
import org.springframework.stereotype.Service;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.device.scanner.dto.ScanDevice;

/**
 *
 * @author Anusha Sunkada
 * @since 1.2.0
 */
@Service
public class DocumentScannerServiceImpl extends DocumentScannerService {

	private static final Logger LOGGER = AppConfig.getLogger(DocumentScannerServiceImpl.class);
	private static final String DEFAULT_EXCEPTIONAL_DEVICE_TYPES = ".*fficejet.*;.*Jet.*";
	private static final String DOC_SCAN_MODE= "mosip.registration.document_scanner_mode";

	@Override
	@Deprecated
	public boolean isConnected() {
		Manager manager = null;
		try {
			manager = getScanManager();
			return manager.listDevices().isEmpty() ? false : true;

		} catch (Exception e) {
			LOGGER.error(LOG_REG_DOC_SCAN_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					ExceptionUtils.getStackTrace(e));
		} finally {
			if (manager != null)
				manager.close();
		}
		return false;
	}

	@Override
	@Deprecated
	public BufferedImage scan() {
		Manager manager = null;
		try {
			manager = getScanManager();
			List<Device> devices = manager.listDevices();

			if (!devices.isEmpty()) {
				return scanImage(devices.get(0));
			}

		} catch (Exception e) {
			LOGGER.error(LOG_REG_DOC_SCAN_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					ExceptionUtils.getStackTrace(e));
		} finally {
			if (manager != null)
				manager.close();
		}
		return null;
	}

	@Override
	public List<ScanDevice> getDevices() {
		List<ScanDevice> scanDevices = new ArrayList<>();
		Manager manager = null;
		try {
			manager = getScanManager();
			List<Device> devices = manager.listDevices();
			for (Device device : devices) {
				ScanDevice scanDevice = new ScanDevice();
				scanDevice.setName(device.toString());
				scanDevice.setId(device.toString());
				scanDevice.setWIA(true);
				scanDevices.add(scanDevice);
			}
		} catch (Throwable ex) {
			LOGGER.error(LOG_REG_DOC_SCAN_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					ExceptionUtils.getStackTrace(ex));
		} finally {
			if (manager != null)
				manager.close();
		}
		return scanDevices;
	}

	@Override
	public BufferedImage scan(String deviceName) {
		Manager manager = null;
		try {
			manager = getScanManager();
			Optional<Device> result = manager.listDevices().stream()
					.filter(device -> device.toString().equals(deviceName)).findFirst();

			if (result.isPresent()) {
				return scanImage(result.get());
			}
		} catch (Exception e) {
			LOGGER.error(LOG_REG_DOC_SCAN_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					ExceptionUtils.getStackTrace(e));
		} finally {
			if (manager != null)
				manager.close();
		}
		return null;
	}

	private Manager getScanManager() throws RegBaseCheckedException {
		String deviceTypes_env = System.getenv("mosip.scanner.device.types");
		String deviceTypes_conf = (String) ApplicationContext.map()
				.get(RegistrationConstants.EXCEPTIONAL_SCANNER_DEVICE_TYPES);
		String deviceTypes = Objects.nonNull(deviceTypes_conf) ? deviceTypes_conf
				: (Objects.nonNull(deviceTypes_env) ? deviceTypes_env : DEFAULT_EXCEPTIONAL_DEVICE_TYPES);

		if (Objects.nonNull(deviceTypes)) {
			String[] deviceTypesArr = deviceTypes.split(";");
			for (String deviceType : deviceTypesArr) {
				Configuration.addDeviceType(deviceType, true);
			}
		}
		LOGGER.info(LOG_REG_DOC_SCAN_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
				"Exceptional scanner device types : " + deviceTypes);
		try {
			return Manager.getInstance();
		} catch (Throwable e) {
			LOGGER.error("Exception while getting morena manager instance", e);
			throw new RegBaseCheckedException("", "Failed to get morena device manager instance", e);
		}
	}

	public BufferedImage scanImage(Device paramDevice) throws Exception {
		setScannerSettings(paramDevice);
		ImageTransferHandler imageTransferHandler = new ImageTransferHandler();
		synchronized (imageTransferHandler) {
			((DeviceBase) paramDevice).startTransfer(imageTransferHandler);
			while (!imageTransferHandler.transferDone)
				imageTransferHandler.wait();
		}
		if (imageTransferHandler.image != null)
			return imageTransferHandler.image;
		throw new Exception(imageTransferHandler.error);
	}

	private void setScannerSettings(Device scanDevice) {
		if(scanDevice instanceof Scanner) {
			((Scanner)scanDevice).setResolution(Integer.parseInt(String.valueOf(
					ApplicationContext.map().get(RegistrationConstants.DOC_SCAN_DPI))));

			((Scanner)scanDevice).setMode(Integer.parseInt(String.valueOf(
					ApplicationContext.map().getOrDefault(DOC_SCAN_MODE, Scanner.RGB_8))));

		}
	}
}

class ImageTransferHandler implements TransferDoneListener {
	BufferedImage image;
	int code;
	String error;
	boolean transferDone = false;

	public void transferDone(File param1File) {
		if (param1File != null)
			try {
				this.image = ImageIO.read(param1File);
			} catch (IOException iOException) {
				this.error = iOException.getLocalizedMessage();
			}
		notifyRequestor();
	}

	public void transferFailed(int param1Int, String param1String) {
		this.code = param1Int;
		this.error = param1String;
		notifyRequestor();
	}

	private synchronized void notifyRequestor() {
		this.transferDone = true;
		notify();
	}
}