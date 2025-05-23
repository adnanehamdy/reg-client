package io.mosip.registration.controller;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.collections4.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.commons.packet.constants.PacketManagerConstants;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.templatemanager.spi.TemplateManagerBuilder;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.StringUtils;
import io.mosip.registration.audit.AuditManagerService;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.LoggerConstants;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.device.BiometricsController;
import io.mosip.registration.controller.device.ScanPopUpViewController;
import io.mosip.registration.controller.eodapproval.RegistrationApprovalController;
import io.mosip.registration.controller.reg.AlertController;
import io.mosip.registration.controller.reg.DemographicDetailController;
import io.mosip.registration.controller.reg.DocumentScanController;
import io.mosip.registration.controller.reg.HeaderController;
import io.mosip.registration.controller.reg.HomeController;
import io.mosip.registration.controller.reg.PacketHandlerController;
import io.mosip.registration.controller.reg.RegistrationPreviewController;
import io.mosip.registration.controller.reg.UserOnboardParentController;
import io.mosip.registration.controller.reg.Validations;
import io.mosip.registration.dto.AuthenticationValidatorDTO;
import io.mosip.registration.dto.RegistrationDTO;
import io.mosip.registration.dto.ResponseDTO;
import io.mosip.registration.dto.UiSchemaDTO;
import io.mosip.registration.dto.VersionMappings;
import io.mosip.registration.dto.biometric.BiometricExceptionDTO;
import io.mosip.registration.dto.biometric.BiometricInfoDTO;
import io.mosip.registration.dto.biometric.FaceDetailsDTO;
import io.mosip.registration.dto.packetmanager.BiometricsDto;
import io.mosip.registration.exception.PreConditionCheckException;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.exception.RegistrationExceptionConstants;
import io.mosip.registration.exception.RemapException;
import io.mosip.registration.scheduler.SchedulerUtil;
import io.mosip.registration.service.BaseService;
import io.mosip.registration.service.IdentitySchemaService;
import io.mosip.registration.service.bio.BioService;
import io.mosip.registration.service.config.GlobalParamService;
import io.mosip.registration.service.operator.UserOnboardService;
import io.mosip.registration.service.remap.CenterMachineReMapService;
import io.mosip.registration.service.security.AuthenticationService;
import io.mosip.registration.service.sync.SyncStatusValidatorService;
import io.mosip.registration.service.template.TemplateService;
import io.mosip.registration.util.acktemplate.TemplateGenerator;
import io.mosip.registration.util.common.PageFlow;
import io.mosip.registration.util.healthcheck.RegistrationAppHealthCheckUtil;
import io.mosip.registration.util.restclient.AuthTokenUtilService;
import io.mosip.registration.util.restclient.ServiceDelegateUtil;
import io.mosip.registration.validator.RequiredFieldValidator;
import javafx.animation.PauseTransition;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Base class for all controllers.
 *
 * @author Sravya Surampalli
 * @since 1.0.0
 */

@Component
public class BaseController {

	private static final Logger LOGGER = AppConfig.getLogger(BaseController.class);
	private static final String ALERT_STAGE = "alertStage";

	@FXML
	public Text scanningMsg;

	@Autowired
	private SyncStatusValidatorService syncStatusValidatorService;
	@Autowired
	protected AuditManagerService auditFactory;
	@Autowired
	protected GlobalParamService globalParamService;

	@Autowired
	protected ServiceDelegateUtil serviceDelegateUtil;

	@Autowired
	protected FXComponents fXComponents;

	@Autowired
	private DemographicDetailController demographicDetailController;
	@Autowired
	public RegistrationPreviewController registrationPreviewController;

	@Autowired
	private BiometricsController guardianBiometricsController;

	@Autowired
	private TemplateService templateService;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private TemplateManagerBuilder templateManagerBuilder;

	@Autowired
	private TemplateGenerator templateGenerator;

	@Autowired
	private UserOnboardService userOnboardService;

	@Autowired
	private CenterMachineReMapService centerMachineReMapService;

	@Autowired
	private PacketHandlerController packetHandlerController;

	@Autowired
	private HeaderController headerController;

	@Autowired
	private HomeController homeController;

	@Autowired
	private AlertController alertController;

	@Autowired
	private ScanPopUpViewController scanPopUpViewController;

	@Autowired
	private RegistrationApprovalController registrationApprovalController;

	@Autowired
	private Validations validations;

	@Autowired
	protected PageFlow pageFlow;
	
	@Autowired
	private UserOnboardParentController userOnboardParentController;

	@Value("${mosip.registration.css_file_path:}")
	private String cssName;
	
	@Value("${mosip.registration.verion.upgrade.default-version-mappings}")
	private String defaultVersionMappings;

	@Autowired
	private BioService bioService;

	@Autowired
	private RestartController restartController;

	@Autowired
	private IdentitySchemaService identitySchemaService;

	@Autowired
	private RequiredFieldValidator requiredFieldValidator;

	@Autowired
	private Validations validation;

	@Autowired
	private BaseService baseService;

	@Autowired
	private AuthTokenUtilService authTokenUtilService;

	@Autowired
	private DocumentScanController documentScanController;

	protected ApplicationContext applicationContext = ApplicationContext.getInstance();

	public Text getScanningMsg() {
		return scanningMsg;
	}

	public void setScanningMsg(String message) {
		scanningMsg.setText(message);
	}

	protected Scene scene;

	private List<String> pageDetails = new ArrayList<>();

	private Stage alertStage;

	private static boolean isAckOpened = false;

	private List<UiSchemaDTO> uiSchemaDTOs;

	private static Map<String, UiSchemaDTO> validationMap;

	private static TreeMap<String, String> mapOfbiometricSubtypes = new TreeMap<>();

	public static TreeMap<String, String> getMapOfbiometricSubtypes() {
		return mapOfbiometricSubtypes;
	}

	private static HashMap<String, String> labelMap = new HashMap<>();

	public static String getFromLabelMap(String key) {
		return labelMap.get(key);
	}

	public static void putIntoLabelMap(String key, String value) {
		labelMap.put(key, value);
	}

	private static List<String> ALL_BIO_ATTRIBUTES = null;

	static {
		ALL_BIO_ATTRIBUTES = new ArrayList<String>();
		ALL_BIO_ATTRIBUTES.addAll(RegistrationConstants.leftHandUiAttributes);
		ALL_BIO_ATTRIBUTES.addAll(RegistrationConstants.rightHandUiAttributes);
		ALL_BIO_ATTRIBUTES.addAll(RegistrationConstants.twoThumbsUiAttributes);
		ALL_BIO_ATTRIBUTES.addAll(RegistrationConstants.eyesUiAttributes);
		ALL_BIO_ATTRIBUTES.add(RegistrationConstants.FACE_EXCEPTION);
	}

	/**
	 * Set Validations map
	 * 
	 * @param validations is a map id's and regex validations
	 */
	public void setValidations(Map<String, UiSchemaDTO> validations) {
		validationMap = validations;
	}

	public Map<String, UiSchemaDTO> getValidationMap() {
		return validationMap;
	}

	/**
	 * @return the alertStage
	 */
	public Stage getAlertStage() {
		return alertStage;
	}

	/**
	 * Adding events to the stage.
	 *
	 * @return the stage
	 */
	protected Stage getStage() {
		EventHandler<Event> event = new EventHandler<Event>() {
			@Override
			public void handle(Event event) {
				SchedulerUtil.setCurrentTimeToStartTime();
			}
		};
		fXComponents.getStage().addEventHandler(EventType.ROOT, event);
		return fXComponents.getStage();
	}

	/**
	 * Load screen.
	 *
	 * @param screen the screen
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected void loadScreen(String screen) throws IOException {
		Parent createRoot = BaseController.load(getClass().getResource(screen),
				applicationContext.getApplicationLanguageBundle());
		getScene(createRoot);
	}

	/**
	 * Gets the scene.
	 *
	 * @param borderPane the border pane
	 * @return the scene
	 */
	protected Scene getScene(Parent borderPane) {
		scene = fXComponents.getScene();
		if (scene == null) {
			scene = new Scene(borderPane);
			fXComponents.setScene(scene);
		}
		scene.setRoot(borderPane);
		fXComponents.getStage().setScene(scene);
		scene.getStylesheets().add(ClassLoader.getSystemClassLoader().getResource(getCssName()).toExternalForm());
		return scene;
	}

	/**
	 * Loading FXML files along with beans.
	 *
	 * @param <T> the generic type
	 * @param url the url
	 * @return T
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static <T> T load(URL url) throws IOException {
		FXMLLoader loader = new FXMLLoader(url, ApplicationContext.applicationLanguageBundle());
		loader.setControllerFactory(Initialization.getApplicationContext()::getBean);
		return loader.load();
	}

	/**
	 * Loading FXML files along with beans.
	 *
	 * @param <T>      the generic type
	 * @param url      the url
	 * @param resource the resource
	 * @return T
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static <T> T load(URL url, ResourceBundle resource) throws IOException {
		FXMLLoader loader = new FXMLLoader(url, resource);
		loader.setControllerFactory(Initialization.getApplicationContext()::getBean);
		return loader.load();
	}

	/**
	 * /* Alert creation with specified title, header, and context.
	 *
	 * @param title   alert title
	 * @param context alert context
	 */
	protected void generateAlert(String title, String context) {
		try {
			closeAlreadyExistedAlert();
			alertStage = new Stage();
			Pane authRoot = BaseController.load(getClass().getResource(RegistrationConstants.ALERT_GENERATION));
			Scene scene = new Scene(authRoot);
			scene.getStylesheets().add(ClassLoader.getSystemClassLoader().getResource(getCssName()).toExternalForm());
			alertStage.initStyle(StageStyle.UNDECORATED);
			alertStage.setScene(scene);
			alertStage.initModality(Modality.WINDOW_MODAL);
			alertController.getAlertGridPane().setPrefHeight(context.length() / 2 + 110);
			if (scanPopUpViewController.getPopupStage() != null
					&& scanPopUpViewController.getPopupStage().isShowing()) {
				alertStage.initOwner(scanPopUpViewController.getPopupStage());
				alertTypeCheck(title, context, alertStage);
			} else if (registrationApprovalController.getPrimaryStage() != null
					&& registrationApprovalController.getPrimaryStage().isShowing()) {
				alertStage.initOwner(registrationApprovalController.getPrimaryStage());
				alertTypeCheck(title, context, alertStage);
			} else {
				alertStage.initOwner(fXComponents.getStage());
				alertTypeCheck(title, context, alertStage);
			}
		} catch (IOException ioException) {
			LOGGER.error("REGISTRATION - ALERT - BASE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
		} catch (RuntimeException runtimeException) {
			LOGGER.error("REGISTRATION - ALERT - BASE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
		}
	}

	/**
	 * /* Alert creation with specified title, header, and context.
	 *
	 * @param title   alert title
	 * @param context alert context
	 */
	protected boolean generateAlert(String title, String context, ToRun<Boolean> run, BaseController controller) {
		boolean isValid = false;
		try {
			closeAlreadyExistedAlert();
			alertStage = new Stage();
			Pane authRoot = BaseController.load(getClass().getResource(RegistrationConstants.ALERT_GENERATION));
			Scene scene = new Scene(authRoot);
			scene.getStylesheets().add(ClassLoader.getSystemClassLoader().getResource(getCssName()).toExternalForm());
			alertStage.initStyle(StageStyle.UNDECORATED);
			alertStage.setScene(scene);
			alertStage.initModality(Modality.WINDOW_MODAL);

			alertController.getAlertGridPane().setPrefHeight(context.length() / 2 + 110);
			controller.setScanningMsg(RegistrationUIConstants.VALIDATION_MESSAGE);
			alertTypeCheck(title, context, alertStage);
			isValid = run.toRun();
		} catch (IOException ioException) {
			LOGGER.error("REGISTRATION - ALERT - BASE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
		} catch (RuntimeException runtimeException) {
			LOGGER.error("REGISTRATION - ALERT - BASE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
		}
		return isValid;
	}

	private void alertTypeCheck(String title, String context, Stage alertStage) {
		if (context.contains(RegistrationConstants.INFO) || (!context.contains(RegistrationConstants.INFO)
				&& !context.contains(RegistrationConstants.SUCCESS.toUpperCase())
				&& !context.contains(RegistrationConstants.ERROR.toUpperCase()))) {
			if (SessionContext.isSessionContextAvailable()) {
				SessionContext.map().put(ALERT_STAGE, alertStage);
			}
			alertController.generateAlertResponse(title, context);
			alertStage.showAndWait();
		} else {
			if (SessionContext.isSessionContextAvailable()) {
				SessionContext.map().put(ALERT_STAGE, alertStage);
			}
			alertController.generateAlertResponse(title, context);
			alertStage.showAndWait();
		}
		alertController.alertWindowExit();
	}

	/**
	 * /* Alert creation with specified title, header, and context.
	 *
	 * @param title   the title
	 * @param context alert context
	 */
	protected void generateAlertLanguageSpecific(String title, String context) {
		generateAlert(title, RegistrationUIConstants.getMessageLanguageSpecific(context));
	}

	/**
	 * Alert specific for page navigation confirmation
	 * 
	 * @return
	 */
	protected boolean pageNavigantionAlert() {
		if (!fXComponents.getScene().getRoot().getId().equals("mainBox") && !SessionContext.map()
				.get(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ).equals(RegistrationConstants.ENABLE)) {

			Alert alert = createAlert(AlertType.CONFIRMATION, RegistrationUIConstants.INFORMATION,
					RegistrationUIConstants.ALERT_NOTE_LABEL, RegistrationUIConstants.PAGE_NAVIGATION_MESSAGE,
					RegistrationConstants.PAGE_NAVIGATION_CONFIRM, RegistrationConstants.PAGE_NAVIGATION_CANCEL);

			alert.show();
			Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();
			Double xValue = screenSize.getWidth() / 2 - alert.getWidth() + 250;
			Double yValue = screenSize.getHeight() / 2 - alert.getHeight();
			alert.hide();
			alert.setX(xValue);
			alert.setY(yValue);
			alert.showAndWait();
			/* Get Option from user */
			ButtonType result = alert.getResult();
			if (result == ButtonType.OK) {
				return true;
			} else {
				return false;
			}
		}
		return true;
	}

	/**
	 * Alert creation with specified context.
	 *
	 * @param parentPane the parent pane
	 * @param id         the id
	 * @param context    alert context
	 */
	protected void generateAlert(Pane parentPane, String id, String context) {
		String type = "#TYPE#";
		if (id.contains(RegistrationConstants.ONTYPE)) {
			id = id.replaceAll(RegistrationConstants.UNDER_SCORE + RegistrationConstants.ONTYPE,
					RegistrationConstants.EMPTY);
		}

		String[] parts = id.split("__");
		if (parts.length > 1 && parts[1].matches(RegistrationConstants.DTAE_MONTH_YEAR_REGEX)) {
			id = parts[0] + "__" + RegistrationConstants.DOB;
			parentPane = (Pane) parentPane.getParent().getParent();
		}
		Label label = ((Label) (parentPane.lookup(RegistrationConstants.HASH + id + RegistrationConstants.MESSAGE)));
		if (label != null) {
			if (!(label.isVisible() && id.equals(RegistrationConstants.DOB))) {
				String[] split = context.split(type);
				label.setText(split[0]);
				label.setWrapText(true);
			}

			Tooltip tool = new Tooltip(context.contains(type) ? context.split(type)[0] : context);
			tool.getStyleClass().add(RegistrationConstants.TOOLTIP);
			label.setTooltip(tool);
			//label.setVisible(true);
		}
	}

	/**
	 * Validate sync status.
	 *
	 * @return the response DTO
	 */
	protected ResponseDTO validateSyncStatus() {

		return syncStatusValidatorService.validateSyncStatus();
	}

	/**
	 * Validating Id for Screen Authorization.
	 *
	 * @param screenId the screenId
	 * @return boolean
	 */
	protected boolean validateScreenAuthorization(String screenId) {

		return SessionContext.userContext().getAuthorizationDTO().getAuthorizationScreenId().contains(screenId);
	}

	/**
	 * Regex validation with specified field and pattern.
	 *
	 * @param field        concerned field
	 * @param regexPattern pattern need to checked
	 * @return true, if successful
	 */
	protected boolean validateRegex(Control field, String regexPattern) {
		if (field instanceof TextField) {
			if (!((TextField) field).getText().matches(regexPattern))
				return true;
		} else {
			if (field instanceof PasswordField) {
				if (!((PasswordField) field).getText().matches(regexPattern))
					return true;
			}
		}
		return false;
	}

	/**
	 * {@code autoCloseStage} is to close the stage automatically by itself for a
	 * configured amount of time.
	 *
	 * @param stage the stage
	 */
	protected void autoCloseStage(Stage stage) {
		PauseTransition delay = new PauseTransition(Duration.seconds(5));
		delay.setOnFinished(event -> stage.close());
		delay.play();
	}

	/**
	 * {@code globalParams} is to retrieve required global config parameters for
	 * login from config table.
	 *
	 */
	protected void getGlobalParams() {
		ApplicationContext.setApplicationMap(globalParamService.getGlobalParams());
	}

	/**
	 * Get the details form Global Param Map is the values existed or not.
	 *
	 * @return Response DTO
	 */
	protected ResponseDTO getSyncConfigData() {
		return globalParamService.synchConfigData(false);
	}

	/**
	 * Opens the home page screen.
	 */
	public void goToHomePage() {
		try {

			guardianBiometricsController.clearBioCaptureInfo();

			if (isAckOpened() || pageNavigantionAlert()) {
				setIsAckOpened(false);
				BaseController.load(getClass().getResource(RegistrationConstants.HOME_PAGE));
				if (!(boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
					clearOnboardData();
					clearRegistrationData();

				} else {
					SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ,
							RegistrationConstants.ENABLE);
				}
			}

		} catch (IOException ioException) {
			LOGGER.error("REGISTRATION - REDIRECTHOME - BASE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE);
		} catch (RuntimeException runtimException) {
			LOGGER.error("REGISTRATION - REDIRECTHOME - BASE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					runtimException.getMessage() + ExceptionUtils.getStackTrace(runtimException));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.UNABLE_LOAD_HOME_PAGE);
		}
	}

	/**
	 * Opens the home page screen.
	 */
	public void loadLoginScreen() {
		try {
			Parent root = load(getClass().getResource(RegistrationConstants.INITIAL_PAGE));
			getStage().setScene(getScene(root));
		} catch (IOException ioException) {
			LOGGER.error("REGISTRATION - REDIRECTLOGIN - BASE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
		}
	}

	/**
	 * This method is used clear all the new registration related mapm values and
	 * navigates to the home page.
	 */
	public void goToHomePageFromRegistration() {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Going to home page");

		goToHomePage();

	}

	/**
	 * This method is used clear all the new onboard related mapm values and
	 * navigates to the home page.
	 */
	public void goToHomePageFromOnboard() {
		LOGGER.info(RegistrationConstants.REGISTRATION_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Going to home page");

		goToHomePage();
	}

	/**
	 * Clear registration data.
	 */
	protected void clearRegistrationData() {

		SessionContext.map().remove(RegistrationConstants.REGISTRATION_ISEDIT);
		SessionContext.map().remove(RegistrationConstants.REGISTRATION_PANE1_DATA);
		SessionContext.map().remove(RegistrationConstants.REGISTRATION_PANE2_DATA);
		SessionContext.map().remove(RegistrationConstants.REGISTRATION_AGE_DATA);
		SessionContext.map().remove(RegistrationConstants.REGISTRATION_DATA);
		SessionContext.map().remove(RegistrationConstants.IS_Child);
		SessionContext.map().remove(RegistrationConstants.DD);
		SessionContext.map().remove(RegistrationConstants.MM);
		SessionContext.map().remove(RegistrationConstants.YYYY);
		SessionContext.map().remove(RegistrationConstants.DOB_TOGGLE);
		SessionContext.map().remove(RegistrationConstants.UIN_UPDATE_DEMOGRAPHICDETAIL);
		SessionContext.map().remove(RegistrationConstants.UIN_UPDATE_DOCUMENTSCAN);
		SessionContext.map().remove(RegistrationConstants.UIN_UPDATE_FINGERPRINTCAPTURE);
		SessionContext.map().remove(RegistrationConstants.UIN_UPDATE_BIOMETRICEXCEPTION);
		SessionContext.map().remove(RegistrationConstants.UIN_UPDATE_FACECAPTURE);
		SessionContext.map().remove(RegistrationConstants.UIN_UPDATE_IRISCAPTURE);
		SessionContext.map().remove(RegistrationConstants.UIN_UPDATE_REGISTRATIONPREVIEW);
		SessionContext.map().remove(RegistrationConstants.UIN_UPDATE_OPERATORAUTHENTICATIONPANE);
		SessionContext.map().remove(RegistrationConstants.OLD_BIOMETRIC_EXCEPTION);
		SessionContext.map().remove(RegistrationConstants.NEW_BIOMETRIC_EXCEPTION);

		clearAllValues();

		guardianBiometricsController.clearBioCaptureInfo();

		SessionContext.userMap().remove(RegistrationConstants.TOGGLE_BIO_METRIC_EXCEPTION);
		SessionContext.userMap().remove(RegistrationConstants.IS_LOW_QUALITY_BIOMETRICS);
		SessionContext.map().remove(RegistrationConstants.DUPLICATE_FINGER);

		pageFlow.loadPageFlow();

	}

	/**
	 * Clear onboard data.
	 */
	protected void clearOnboardData() {
		SessionContext.map().put(RegistrationConstants.ONBOARD_USER_UPDATE, false);
		SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ, RegistrationConstants.DISABLE);
		SessionContext.map().put(RegistrationConstants.ONBOARD_USER, false);
		SessionContext.map().remove(RegistrationConstants.USER_ONBOARD_DATA);
		SessionContext.map().remove(RegistrationConstants.OLD_BIOMETRIC_EXCEPTION);
		SessionContext.map().remove(RegistrationConstants.NEW_BIOMETRIC_EXCEPTION);
	}

	/**
	 * Load child.
	 *
	 * @param url the url
	 * @return the FXML loader
	 */
	public static FXMLLoader loadChild(URL url) {
		FXMLLoader loader = new FXMLLoader(url, ApplicationContext.applicationLanguageBundle());
		loader.setControllerFactory(Initialization.getApplicationContext()::getBean);
		return loader;
	}

	/**
	 * Gets the finger print status.
	 *
	 */
	public void updateAuthenticationStatus() {

	}

	/**
	 * Scans documents.
	 *
	 * @param popupStage the stage
	 */
	public void scan(Stage popupStage) {

	}

	/**
	 * This method used to clear the images that are captured using webcam.
	 *
	 * @param imageType Type of image that is to be cleared
	 */
	public void clearPhoto(String imageType) {
		// will be implemented in the derived class.
	}

	/**
	 * it will wait for the mentioned time to get the capture image from Bio Device.
	 *
	 * @param count             the count
	 * @param waitTimeInSec     the wait time in sec
	 * @param fingerprintFacade the fingerprint facade
	 */
//	protected void waitToCaptureBioImage(int count, int waitTimeInSec, FingerprintFacade fingerprintFacade) {
//		int counter = 0;
//		while (counter < 5) {
//			if (!RegistrationConstants.EMPTY.equals(fingerprintFacade.getMinutia())
//					|| !RegistrationConstants.EMPTY.equals(fingerprintFacade.getErrorMessage())) {
//				break;
//			} else {
//				try {
//					Thread.sleep(2000);
//				} catch (InterruptedException interruptedException) {
//					LOGGER.error("FINGERPRINT_AUTHENTICATION_CONTROLLER - ERROR_SCANNING_FINGER", APPLICATION_NAME,
//							APPLICATION_ID,
//							interruptedException.getMessage() + ExceptionUtils.getStackTrace(interruptedException));
//				}
//			}
//			counter++;
//		}
//	}

	/**
	 * Convert bytes to image.
	 *
	 * @param imageBytes the image bytes
	 * @return the image
	 */
	protected Image convertBytesToImage(byte[] imageBytes) {
		Image image = null;
		if (imageBytes != null) {
			image = new Image(new ByteArrayInputStream(imageBytes));
		}
		return image;
	}

	/**
	 * Online availability check.
	 *
	 * @return the timer
	 */
	protected Timer onlineAvailabilityCheck() {
		Timer timer = new Timer();
		fXComponents.setTimer(timer);
		return timer;
	}

	/**
	 * Stop timer.
	 */
	protected void stopTimer() {
		if (fXComponents.getTimer() != null) {
			fXComponents.getTimer().cancel();
			fXComponents.getTimer().purge();
			fXComponents.setTimer(null);
		}
	}

	/**
	 * Gets the timer.
	 *
	 * @return the timer
	 */
	public Timer getTimer() {
		return fXComponents.getTimer() == null ? onlineAvailabilityCheck() : fXComponents.getTimer();
	}

	/**
	 * to validate the password in case of password based authentication.
	 *
	 * @param username the username
	 * @param password the password
	 * @return the string
	 */
	protected String validatePwd(String username, String password) {

		LOGGER.info("REGISTRATION - OPERATOR_AUTHENTICATION", APPLICATION_NAME, APPLICATION_ID, "Validating Password");

		AuthenticationValidatorDTO authenticationValidatorDTO = new AuthenticationValidatorDTO();
		authenticationValidatorDTO.setUserId(username);
		authenticationValidatorDTO.setPassword(password);

		if (authenticationService.validatePassword(authenticationValidatorDTO)
				.equals(RegistrationConstants.PWD_MATCH)) {
			return RegistrationConstants.SUCCESS;
		}
		return RegistrationConstants.FAILURE;
	}

	/**
	 * Clear all values.
	 */
	protected void clearAllValues() {
		if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
			// ((BiometricDTO)
			// SessionContext.map().get(RegistrationConstants.USER_ONBOARD_DATA))
			// .setOperatorBiometricDTO(createBiometricInfoDTO());
			// biometricExceptionController.clearSession();
			// fingerPrintCaptureController.clearFingerPrintDTO();
			// irisCaptureController.clearIrisData();
			// faceCaptureController.clearPhoto(RegistrationConstants.APPLICANT_IMAGE);
			guardianBiometricsController.clearCapturedBioData();
		}

		if (SessionContext.map().get(RegistrationConstants.REGISTRATION_DATA) != null) {
			RegistrationDTO registrationDTO = (RegistrationDTO) SessionContext.map().get(RegistrationConstants.REGISTRATION_DATA);
			registrationDTO.getDocuments().clear();
			registrationDTO.getBiometrics().clear();
			registrationDTO.getBiometricExceptions().clear();
			registrationDTO.getDemographics().clear();
			SessionContext.map().remove(RegistrationConstants.REGISTRATION_DATA);
			guardianBiometricsController.clearCapturedBioData();
		}

		if(documentScanController.getScannedPages() != null) {
			documentScanController.getScannedPages().clear();
		}
	}

	/**
	 * Creates the biometric info DTO.
	 *
	 * @return the biometric info DTO
	 */
	protected BiometricInfoDTO createBiometricInfoDTO() {
		BiometricInfoDTO biometricInfoDTO = new BiometricInfoDTO();
		biometricInfoDTO.setBiometricExceptionDTO(new ArrayList<>());
		biometricInfoDTO.setFingerprintDetailsDTO(new ArrayList<>());
		biometricInfoDTO.setIrisDetailsDTO(new ArrayList<>());
		biometricInfoDTO.setFace(new FaceDetailsDTO());
		biometricInfoDTO.setExceptionFace(new FaceDetailsDTO());
		return biometricInfoDTO;
	}

	/**
	 * Gets the notification template.
	 *
	 * @param templateCode the template code
	 * @return the notification template
	 */
	/*
	 * protected Writer getNotificationTemplate(String templateCode) {
	 * RegistrationDTO registrationDTO = getRegistrationDTOFromSession(); Writer
	 * writeNotificationTemplate = new StringWriter(); try { // get the data for
	 * notification template String platformLanguageCode =
	 * ApplicationContext.applicationLanguage(); String notificationTemplate =
	 * templateService.getHtmlTemplate(templateCode, platformLanguageCode); if
	 * (notificationTemplate != null && !notificationTemplate.isEmpty()) { //
	 * generate the notification template writeNotificationTemplate =
	 * templateGenerator.generateNotificationTemplate(notificationTemplate,
	 * registrationDTO, templateManagerBuilder); }
	 * 
	 * } catch (RegBaseUncheckedException regBaseUncheckedException) {
	 * LOGGER.error("REGISTRATION - UI - GENERATE_NOTIFICATION", APPLICATION_NAME,
	 * APPLICATION_ID, regBaseUncheckedException.getMessage() +
	 * ExceptionUtils.getStackTrace(regBaseUncheckedException)); } catch
	 * (RegBaseCheckedException regBaseCheckedException) {
	 * LOGGER.error("REGISTRATION - UI- GENERATE_NOTIFICATION", APPLICATION_NAME,
	 * APPLICATION_ID, regBaseCheckedException.getMessage() +
	 * ExceptionUtils.getStackTrace(regBaseCheckedException)); } return
	 * writeNotificationTemplate; }
	 */

	/**
	 * Gets the registration DTO from session.
	 *
	 * @return the registration DTO from session
	 */
	protected RegistrationDTO getRegistrationDTOFromSession() {
		RegistrationDTO registrationDTO = null;
		if (SessionContext.map() != null || !SessionContext.map().isEmpty()) {
			registrationDTO = (RegistrationDTO) SessionContext.map().get(RegistrationConstants.REGISTRATION_DATA);
		}
		return registrationDTO;

	}


	/**
	 * to return to the next page based on the current page and action for User
	 * Onboarding.
	 *
	 * @param currentPage - Id of current Anchorpane
	 * @param action      - action to be performed previous/next
	 * @return id of next Anchorpane
	 */

	@SuppressWarnings("unchecked")
	protected String getOnboardPageDetails(String currentPage, String action) {

		LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
				"Updating OnBoard flow based on visibility and returning next page details");

		LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
				"Returning Next page by action " + action);

		String page = null;

		if (action.equalsIgnoreCase(RegistrationConstants.NEXT)) {
			/** Get Next Page if action is "NEXT" */
			page = pageFlow.getNextOnboardPage(currentPage);
		} else if (action.equalsIgnoreCase(RegistrationConstants.PREVIOUS)) {

			/** Get Previous Page if action is "PREVIOUS" */
			page = pageFlow.getPreviousOnboardPage(currentPage);
		}

		page = saveDetails(currentPage, page);
		return page;

	}

	/**
	 * to return to the next page based on the current page and action for New
	 * Registration.
	 *
	 * @param currentPage - Id of current Anchorpane
	 * @param action      - action to be performed previous/next
	 * @return id of next Anchorpane
	 */
	@SuppressWarnings("unchecked")
	protected String getPageByAction(String currentPage, String action) {

		LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
				"Returning Next page by action " + action);

		String page = null;

		if (action.equalsIgnoreCase(RegistrationConstants.NEXT)) {
			/** Get Next Page if action is "NEXT" */
			page = pageFlow.getNextRegPage(currentPage);
		} else if (action.equalsIgnoreCase(RegistrationConstants.PREVIOUS)) {

			/** Get Previous Page if action is "PREVIOUS" */
			page = pageFlow.getPreviousRegPage(currentPage);
		}

		page = saveDetails(currentPage, page);
		return page;

	}

	/**
	 * to return to the next page based on the current page and action.
	 *
	 * @param pageList    - List of Anchorpane Ids
	 * @param currentPage - Id of current Anchorpane
	 * @param action      - action to be performed previous/next
	 * @return id of next Anchorpane
	 */
	private String getReturnPage(List<String> pageList, String currentPage, String action) {

		LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
				"Fetching the next page based on action");

		String returnPage = "";

		if (action.equalsIgnoreCase(RegistrationConstants.NEXT)) {

			LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
					"Fetching the next page based from list of ids for Next action");

			returnPage = pageList.get((pageList.indexOf(currentPage)) + 1);
		} else if (action.equalsIgnoreCase(RegistrationConstants.PREVIOUS)) {

			LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
					"Fetching the next page based from list of ids for Previous action");

			returnPage = pageList.get((pageList.indexOf(currentPage)) - 1);
		}

		saveDetails(currentPage, returnPage);

		LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
				"Returning the corresponding next page based on given action" + returnPage);

		pageDetails.clear();
		return returnPage;
	}

	private String saveDetails(String currentPage, String returnPage) {
		if (returnPage.equalsIgnoreCase(RegistrationConstants.REGISTRATION_PREVIEW)) {

			LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
					"Invoking Save Detail before redirecting to Preview");

			demographicDetailController.saveDetail();
			registrationPreviewController.setUpPreviewContent();

			LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
					"Details saved and content of preview is set");
		} else if (returnPage.equalsIgnoreCase(RegistrationConstants.ONBOARD_USER_SUCCESS)) {

			LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID, "Validating User Onboard data");

			if (executeUserOnboardTask(userOnboardService.getAllBiometrics())) {
				returnPage = RegistrationConstants.EMPTY;
			} else {
				returnPage = currentPage;
			}
		}

		return returnPage;
	}

	private boolean executeUserOnboardTask(List<BiometricsDto> allBiometrics) {
		AtomicBoolean returnPage = new AtomicBoolean(false);
		userOnboardParentController.getParentPane().setDisable(true);
		userOnboardParentController.getProgressIndicatorParentPane().setVisible(true);
		userOnboardParentController.getProgressIndicator().setVisible(true);

		Service<ResponseDTO> taskService = new Service<ResponseDTO>() {
			@Override
			protected Task<ResponseDTO> createTask() {
				return new Task<ResponseDTO>() {
					/*
					 * (non-Javadoc)
					 * 
					 * @see javafx.concurrent.Task#call()
					 */
					@Override
					protected ResponseDTO call() {
						try {
							return userOnboardService.validateWithIDAuthAndSave(allBiometrics);
						} catch (RegBaseCheckedException checkedException) {
							LOGGER.error(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
									ExceptionUtils.getStackTrace(checkedException));
						}
						return null;
					}
				};
			}
		};
		
		userOnboardParentController.getProgressIndicator().progressProperty().bind(taskService.progressProperty());
		taskService.start();
		taskService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent workerStateEvent) {
				ResponseDTO response = taskService.getValue();
				if (response != null && response.getErrorResponseDTOs() != null
						&& response.getErrorResponseDTOs().get(0) != null) {
					LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
							"Displaying Alert if validation is not success");

					generateAlertLanguageSpecific(RegistrationConstants.ERROR,
							response.getErrorResponseDTOs().get(0).getMessage());
					returnPage.set(false);
				} else if (response != null && response.getSuccessResponseDTO() != null) {

					LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
							"User Onboard is success and clearing Onboard data");

					clearOnboardData();
					SessionContext.map().put(RegistrationConstants.ISPAGE_NAVIGATION_ALERT_REQ,
							RegistrationConstants.ENABLE);
					goToHomePage();
					onboardAlertMsg();

					LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
							"Redirecting to Home page after success onboarding");
					returnPage.set(true);
				}
				userOnboardParentController.getParentPane().setDisable(false);
				userOnboardParentController.getProgressIndicatorParentPane().setVisible(false);
				userOnboardParentController.getProgressIndicator().setVisible(false);

				LOGGER.info(LoggerConstants.LOG_REG_LOGIN, APPLICATION_NAME, APPLICATION_ID,
						"Onboarded User biometrics validation and insertion done");
			}
		});
		return returnPage.get();
	}

	/**
	 * to navigate to the next page based on the current page.
	 *
	 * @param pageId     - Parent Anchorpane where other panes are included
	 * @param notTosShow - Id of Anchorpane which has to be hidden
	 * @param show       - Id of Anchorpane which has to be shown
	 * 
	 */
	protected void getCurrentPage(Pane pageId, String notTosShow, String show) {
		LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
				pageId == null ? "null" : pageId.getId());
		LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
				"Navigating from current page >> " + notTosShow + " to show : " + show);

		if (notTosShow != null) {
			((Pane) pageId.lookup(RegistrationConstants.HASH + notTosShow)).setVisible(false);
		}
		if (show != null) {
			((Pane) pageId.lookup(RegistrationConstants.HASH + show)).setVisible(true);
		}

		LOGGER.info(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
				"Navigated to next page >> " + show);
	}



	public void remapMachine() {

		String message = RegistrationUIConstants.REMAP_NO_ACCESS_MESSAGE;

		if (isPacketsPendingForEODOrReRegister()) {
			message += RegistrationConstants.NEW_LINE + RegistrationUIConstants.REMAP_EOD_PROCESS_MESSAGE;
		}
		message += RegistrationConstants.NEW_LINE + RegistrationUIConstants.REMAP_CLICK_OK;
		generateAlert(RegistrationConstants.ALERT_INFORMATION, message);

		disableHomePage(true);

		Service<String> service = new Service<String>() {
			@Override
			protected Task<String> createTask() {
				return new Task<String>() {

					@Override
					protected String call() throws RemapException {

						packetHandlerController.getProgressIndicator().setVisible(true);

						for (int i = 1; i <= 4; i++) {
							/* starts the remap process */
							centerMachineReMapService.handleReMapProcess(i);
							this.updateProgress(i, 4);
						}
						LOGGER.info("BASECONTROLLER_REGISTRATION CENTER MACHINE REMAP : ", APPLICATION_NAME,
								APPLICATION_ID, "center remap process completed");
						return null;
					}
				};
			}
		};
		packetHandlerController.getProgressIndicator().progressProperty().bind(service.progressProperty());

		service.restart();

		service.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent t) {
				handleRemapResponse(service, true);
			}
		});
		service.setOnFailed(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent t) {
				handleRemapResponse(service, false);
			}
		});

	}

	private void handleRemapResponse(Service<String> service, boolean isSuccess) {
		service.reset();
		disableHomePage(false);
		packetHandlerController.getProgressIndicator().setVisible(false);

		if (isSuccess) {
			generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.REMAP_PROCESS_SUCCESS);
			headerController.logoutCleanUp();
		} else {
			generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.REMAP_PROCESS_STILL_PENDING);
		}
	}


	private void disableHomePage(boolean isDisabled) {

		if (null != homeController.getMainBox())
			homeController.getMainBox().setDisable(isDisabled);

	}

	/**
	 * Checks if is packets pending for EOD.
	 *
	 * @return true, if is packets pending for EOD
	 */
	protected boolean isPacketsPendingForEOD() {

		return centerMachineReMapService.isPacketsPendingForEOD();
	}

	protected boolean isPacketsPendingForEODOrReRegister() {

		return isPacketsPendingForEOD() || isPacketsPendingForReRegister();
	}

	/**
	 * Checks if is packets pending for ReRegister.
	 *
	 * @return true, if is packets pending for ReRegister
	 */
	protected boolean isPacketsPendingForReRegister() {

		return centerMachineReMapService.isPacketsPendingForReRegister();
	}

	/**
	 * Popup statge.
	 *
	 */
	public void onboardAlertMsg() {
		packetHandlerController.getUserOnboardMessage().setVisible(true);
		fXComponents.getStage().addEventHandler(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (packetHandlerController.getUserOnboardMessage().isVisible()) {
					packetHandlerController.getUserOnboardMessage().setVisible(false);
				}
			}
		});
	}

	/**
	 * Create alert with given title, header and context.
	 *
	 * @param alertType type of alert
	 * @param title     alert's title
	 * @param header    alert's header
	 * @param context   alert's context
	 * @return alert
	 */
	protected Alert createAlert(AlertType alertType, String title, String header, String context,
			String confirmButtonText, String cancelButtonText) {

		Alert alert = new Alert(alertType);
		alert.setTitle(title);
		alert.setHeaderText(header);
		alert.setContentText(
				context.replaceAll(RegistrationConstants.SPLITTER + (RegistrationConstants.SUCCESS.toUpperCase()), ""));
		alert.setGraphic(null);
		alert.setResizable(true);
		alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
		alert.getDialogPane().setMinWidth(500);
		alert.getDialogPane().getStylesheets()
				.add(ClassLoader.getSystemClassLoader().getResource(getCssName()).toExternalForm());
		Button okButton = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
		okButton.setText(RegistrationUIConstants.getMessageLanguageSpecific(confirmButtonText));

		if (alertType == Alert.AlertType.CONFIRMATION) {
			Button cancelButton = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
			cancelButton.setText(RegistrationUIConstants.getMessageLanguageSpecific(cancelButtonText));
		}
		alert.initStyle(StageStyle.UNDECORATED);
		alert.initModality(Modality.WINDOW_MODAL);
		alert.initOwner(fXComponents.getStage());
		if (SessionContext.isSessionContextAvailable()) {
			SessionContext.map().put("alert", alert);
		}
		return alert;
	}

	/**
	 * Update UIN method flow.
	 */
	protected void updateUINMethodFlow() {
		/*
		 * if ((Boolean) SessionContext.userContext().getUserMap()
		 * .get(RegistrationConstants.TOGGLE_BIO_METRIC_EXCEPTION)) {
		 * SessionContext.map().put(RegistrationConstants.UIN_UPDATE_BIOMETRICEXCEPTION,
		 * true); return; } if
		 * (getRegistrationDTOFromSession().isUpdateUINNonBiometric()) {
		 * SessionContext.map().put(RegistrationConstants.
		 * UIN_UPDATE_PARENTGUARDIAN_DETAILS, true); // // Label guardianBiometricsLabel
		 * = // guardianBiometricsController.getGuardianBiometricsLabel(); //
		 * guardianBiometricsLabel.setText("Biometrics"); // //
		 * guardianBiometricsController.setGuardianBiometricsLabel(
		 * guardianBiometricsLabel);
		 * 
		 * } else if (updateUINNextPage(RegistrationConstants.FINGERPRINT_DISABLE_FLAG)
		 * && !isChild()) {
		 * SessionContext.map().put(RegistrationConstants.UIN_UPDATE_FINGERPRINTCAPTURE,
		 * true); } else if (updateUINNextPage(RegistrationConstants.IRIS_DISABLE_FLAG)
		 * && !isChild()) {
		 * SessionContext.map().put(RegistrationConstants.UIN_UPDATE_IRISCAPTURE, true);
		 * } else if (isChild()) { SessionContext.map().put(RegistrationConstants.
		 * UIN_UPDATE_PARENTGUARDIAN_DETAILS, true); } else if
		 * (RegistrationConstants.ENABLE
		 * .equalsIgnoreCase(getValueFromApplicationContext(RegistrationConstants.
		 * FACE_DISABLE_FLAG))) {
		 * SessionContext.map().put(RegistrationConstants.UIN_UPDATE_FACECAPTURE, true);
		 * } else {
		 */
		SessionContext.map().put(RegistrationConstants.UIN_UPDATE_REGISTRATIONPREVIEW, true);
		registrationPreviewController.setUpPreviewContent();
		// }
	}

	/**
	 * Update UIN next page.
	 *
	 * @param pageFlag the page flag
	 * @return true, if successful
	 */
	protected boolean updateUINNextPage(String pageFlag) {
		return RegistrationConstants.ENABLE.equalsIgnoreCase(getValueFromApplicationContext(pageFlag))
				&& !(Boolean) SessionContext.userMap().get(RegistrationConstants.TOGGLE_BIO_METRIC_EXCEPTION);
	}

	/**
	 * Update UIN next page.
	 *
	 * @return true, if successful
	 */
	protected boolean isChild() {
		return getRegistrationDTOFromSession().isUpdateUINChild();
	}

	/**
	 * Biomertic exception count.
	 *
	 * @param biometric the biometric
	 * @return the long
	 */
	protected long biomerticExceptionCount(String biometric) {
		return getRegistrationDTOFromSession().getBiometricDTO().getApplicantBiometricDTO().getBiometricExceptionDTO()
				.stream().filter(bio -> bio.getBiometricType().equalsIgnoreCase(biometric)).count();
	}

	/**
	 * Gets the value from application context.
	 *
	 * @param key the key
	 * @return the value from application context
	 */
	protected String getValueFromApplicationContext(String key) {

		LOGGER.info(LoggerConstants.LOG_REG_BASE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Fetching value from application Context");

		return applicationContext.getApplicationMap().containsKey(key)
				? (String) applicationContext.getApplicationMap().get(key)
				: null;
	}
	
	/**
	 * Returns the version-mappings in sorted order. Version-Mappings is the
	 * configuration which specifies the list of available versions and their
	 * respective DB version and its release order.
	 * ReleaseOrder starts with "1" which is considered as the oldest version and
	 * "n" being the latest version.
	 * 
	 * @param key
	 * @return sorted version-mappings
	 * @throws JsonMappingException
	 * @throws JsonProcessingException
	 */
	protected Map<String, VersionMappings> getSortedVersionMappings(String key) throws Exception {
		String value = applicationContext.getApplicationMap().containsKey(key)
				? (String) applicationContext.getApplicationMap().get(key)
				: defaultVersionMappings;
		
		if (value == null || value.isBlank()) {
			LOGGER.error(LoggerConstants.LOG_REG_BASE, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "version-mappings key is found empty / null. Please add proper value to proceed.");
			throw new RegBaseCheckedException(RegistrationExceptionConstants.VERSION_MAPPINGS_NOT_FOUND.getErrorCode(),
					RegistrationExceptionConstants.VERSION_MAPPINGS_NOT_FOUND.getErrorMessage());
		}
		
		ObjectMapper mapper = new ObjectMapper(); 
	    TypeReference<HashMap<String,VersionMappings>> typeRef 
	            = new TypeReference<HashMap<String,VersionMappings>>() {};

	    HashMap<String, VersionMappings> versionMappings = mapper.readValue(value, typeRef); 

		if (versionMappings != null) {
			return versionMappings.entrySet().stream()
					.sorted((object1, object2) -> object1.getValue().getReleaseOrder()
							.compareTo(object2.getValue().getReleaseOrder()))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (object1, object2) -> object1,
							LinkedHashMap::new));
		}
		return versionMappings;	
	}

	/**
	 * Gets the quality score.
	 *
	 * @param qulaityScore the qulaity score
	 * @return the quality score
	 */
	protected String getQualityScore(Double qulaityScore) {

		LOGGER.info(LoggerConstants.LOG_REG_BASE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Fetching Quality score while capturing Biometrics");

		return String.valueOf(Math.round(qulaityScore)).concat(RegistrationConstants.PERCENTAGE);
	}

	/**
	 * Updates the Page Flow
	 *
	 * @param pageId id of the page
	 * @param val    value to be set
	 */
	@SuppressWarnings("unchecked")
	protected void updatePageFlow(String pageId, boolean val) {

		LOGGER.info(LoggerConstants.LOG_REG_BASE, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Updating page flow to navigate next or previous");

		// ((Map<String, Map<String, Boolean>>)
		// ApplicationContext.map().get(RegistrationConstants.REGISTRATION_MAP))
		// .get(pageId).put(RegistrationConstants.VISIBILITY, val);

		pageFlow.updateRegMap(pageId, RegistrationConstants.VISIBILITY, val);
	}

	protected void restartApplication() {

		generateAlert(RegistrationConstants.SUCCESS.toUpperCase(), RegistrationUIConstants.RESTART_APPLICATION);
		restartController.restart();

	}

	protected List<BiometricExceptionDTO> getIrisExceptions() {
		if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER)) {
			return null;// return
						// getBiometricDTOFromSession().getOperatorBiometricDTO().getBiometricExceptionDTO();
		} else if (getRegistrationDTOFromSession().isUpdateUINNonBiometric()
				|| (SessionContext.map().get(RegistrationConstants.IS_Child) != null
						&& (boolean) SessionContext.map().get(RegistrationConstants.IS_Child))) {
			return getRegistrationDTOFromSession().getBiometricDTO().getIntroducerBiometricDTO()
					.getBiometricExceptionDTO();
		} else {
			return getRegistrationDTOFromSession().getBiometricDTO().getApplicantBiometricDTO()
					.getBiometricExceptionDTO();
		}
	}

	/**
	 * Any iris exception.
	 *
	 * @param iris the iris
	 * @return true, if successful
	 */
	protected boolean anyIrisException(String iris) {
		return getIrisExceptions().stream().anyMatch(exceptionIris -> exceptionIris.isMarkedAsException() && StringUtils
				.containsIgnoreCase(exceptionIris.getMissingBiometric(), (iris).concat(RegistrationConstants.EYE)));
	}

	public void getExceptionIdentifier(List<String> exception, String exceptionType) {
		exception.add(RegistrationConstants.userOnBoardMap.get(exceptionType));
	}

	/**
	 * To get the current timestamp
	 * 
	 * @return Timestamp returns the current timestamp
	 */
	protected Timestamp getCurrentTimestamp() {
		return Timestamp.from(Instant.now());
	}

	/**
	 * Restricts the re-ordering of the columns in {@link TableView}. This is
	 * generic method.
	 * 
	 * @param table the instance of {@link TableView} for which re-ordering of
	 *              columns had to be restricted
	 */
	@SuppressWarnings("restriction")
	protected void disableColumnsReorder(TableView<?> table) {
		if (table != null) {
			table.widthProperty().addListener((source, oldWidth, newWidth) -> {
				javafx.scene.control.skin.TableHeaderRow header = (javafx.scene.control.skin.TableHeaderRow) table
						.lookup("TableHeaderRow");
			});
		}
	}

	public void closeAlreadyExistedAlert() {
		if (SessionContext.isSessionContextAvailable() && SessionContext.map() != null
				&& SessionContext.map().get(ALERT_STAGE) != null) {
			Stage alertStageFromSession = (Stage) SessionContext.map().get(ALERT_STAGE);
			alertStageFromSession.close();

		}
	}

	public void closeAlertStage() {

		if (alertStage != null && alertStage.isShowing()) {
			alertStage.close();
		}
	}

	public boolean isPrimaryOrSecondaryLanguageEmpty() {

		if (null == ApplicationContext.map().get(RegistrationConstants.PRIMARY_LANGUAGE)
				|| ApplicationContext.map().get(RegistrationConstants.PRIMARY_LANGUAGE).equals("")) {
			return true;
		}
		if (null == ApplicationContext.map().get(RegistrationConstants.SECONDARY_LANGUAGE)
				|| ApplicationContext.map().get(RegistrationConstants.SECONDARY_LANGUAGE).equals("")) {
			return true;
		}
		return false;
	}

	protected String getThresholdKeyByBioType(String bioType) {
		return bioType.equals(RegistrationConstants.FINGERPRINT_SLAB_LEFT)
				? RegistrationConstants.LEFTSLAP_FINGERPRINT_THRESHOLD
				: bioType.equals(RegistrationConstants.FINGERPRINT_SLAB_RIGHT)
						? RegistrationConstants.RIGHTSLAP_FINGERPRINT_THRESHOLD
						: bioType.equals(RegistrationConstants.FINGERPRINT_SLAB_THUMBS)
								? RegistrationConstants.THUMBS_FINGERPRINT_THRESHOLD
								: bioType.toLowerCase().contains(RegistrationConstants.IRIS.toLowerCase())
										? RegistrationConstants.IRIS_THRESHOLD
										: bioType.toLowerCase().contains(RegistrationConstants.FACE.toLowerCase())
												? RegistrationConstants.FACE_THRESHOLD
												: RegistrationConstants.EMPTY;
	}

	public interface ToRun<T> {
		public T toRun();
	}

	/**
	 * Check of wheteher operator was in acknowledgement page
	 *
	 * @return true or false if acknowledge page was opened
	 */
	protected boolean isAckOpened() {
		return isAckOpened;
	}

	/**
	 * Set the operator was in acknowledgement page
	 */
	protected void setIsAckOpened(boolean isAckOpened) {
		this.isAckOpened = isAckOpened;
	}

	public void loadUIElementsFromSchema() {

		try {
			List<UiSchemaDTO> schemaFields = identitySchemaService.getLatestEffectiveUISchema();
			Map<String, UiSchemaDTO> validationsMap = new LinkedHashMap<>();
			for (UiSchemaDTO schemaField : schemaFields) {
				validationsMap.put(schemaField.getId(), schemaField);
				if (schemaField.getType().equals(PacketManagerConstants.BIOMETRICS_DATATYPE)) {
					mapOfbiometricSubtypes.put(schemaField.getSubType(), schemaField.getLabel().get("primary"));
					// if (!listOfBiometricSubTypes.contains(schemaField.getSubType()))
					// listOfBiometricSubTypes.add(schemaField.getSubType());
				}
			}
			validations.setValidations(validationsMap); // Set Validations Map

			// THIS IS NOT REQUIRED
			/*
			 * ApplicationContext.map().put(RegistrationConstants.indBiometrics,
			 * getBioAttributesBySubType(RegistrationConstants.indBiometrics));
			 * ApplicationContext.map().put("parentOrGuardianBiometrics",
			 * getBioAttributesBySubType("parentOrGuardianBiometrics"));
			 */

		} catch (RegBaseCheckedException e) {
			LOGGER.error(LoggerConstants.LOG_REG_BASE, APPLICATION_NAME, APPLICATION_ID,
					ExceptionUtils.getStackTrace(e));
		}
	}

	public SimpleEntry<String, List<String>> getValue(String bio, List<String> attributes) {
		SimpleEntry<String, List<String>> entry = new SimpleEntry<String, List<String>>(bio, attributes);
		return entry;
	}

	protected Map<Entry<String, String>, Map<String, List<List<String>>>> getconfigureAndNonConfiguredBioAttributes(
			List<Entry<String, List<String>>> entryListConstantAttributes) {

		Map<Entry<String, String>, Map<String, List<List<String>>>> mapToProcess = new HashMap<>();

		for (Entry<String, String> uiSchemaSubType : getMapOfbiometricSubtypes().entrySet()) {

			try {
				// List<String> uiAttributes =
				// getBioAttributesBySubType(uiSchemaSubType.getKey());

				List<String> uiAttributes = requiredFieldValidator.isRequiredBiometricField(uiSchemaSubType.getKey(),
						getRegistrationDTOFromSession());

				if (uiAttributes != null && !uiAttributes.isEmpty()) {
					HashMap<String, List<List<String>>> subMap = new HashMap<>();
					for (Entry<String, List<String>> constantAttributes : entryListConstantAttributes) {
						List<String> nonConfigBiometrics = new LinkedList<>();
						List<String> configBiometrics = new LinkedList<>();
						String slabType = constantAttributes.getKey();
						for (String attribute : constantAttributes.getValue()) {
							if (!uiAttributes.contains(attribute)) {
								nonConfigBiometrics.add(attribute);
							} else {
								configBiometrics.add(attribute);
							}
						}
						subMap.put(slabType, Arrays.asList(configBiometrics, nonConfigBiometrics));
					}
					mapToProcess.put(uiSchemaSubType, subMap);
				}
			} catch (RegBaseCheckedException exception) {
				LOGGER.error("REGISTRATION - ALERT - BASE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
						ExceptionUtils.getStackTrace(exception));
			}

		}

		return mapToProcess;
	}

	/*
	 * protected void disablePaneOnBioAttributes(Node pane, List<String>
	 * constantBioAttributes) { return;
	 *//** Put pane disable by default */
	/*
	 * 
	 * pane.setDisable(true);
	 * 
	 *//** Get UI schema individual Biometrics Bio Attributes */
	/*
	 * 
	 * List<String> uiSchemaBioAttributes =
	 * getBioAttributesBySubType(RegistrationConstants.indBiometrics);
	 * 
	 *//** If bio Attribute not mentioned for bio attribute then disable */
	/*
	 * 
	 * if (uiSchemaBioAttributes == null || uiSchemaBioAttributes.isEmpty()) {
	 * pane.setDisable(true); } else {
	 * 
	 * for (String attribute : constantBioAttributes) {
	 * 
	 *//** If bio attribute configured in UI Schema, then enable the pane */

	/*
	 * 
	 * if (uiSchemaBioAttributes.contains(attribute)) { pane.setDisable(false);
	 * 
	 *//** Stop the iteration as we got the attribute *//*
														 * break; } } }
														 * 
														 * }
														 */

	// protected void addExceptionDTOs() {
	// List<String> bioAttributesFromSchema =
	// getSchemaFieldBioAttributes(RegistrationConstants.indBiometrics);
	// List<String> bioList = new ArrayList<String>();
	//
	// /** If bio Attribute not mentioned for bio attribute then disable */
	// bioList.addAll(ALL_BIO_ATTRIBUTES);
	//
	// /** If bio attribute configured in UI Schema, then enable the pane */
	// if (bioAttributesFromSchema != null && !bioAttributesFromSchema.isEmpty())
	// bioList.removeAll(bioAttributesFromSchema);
	//
	// List<BiometricExceptionDTO> biometricExceptionDTOs =
	// biometricExceptionController
	// .getBiometricsExceptionList(bioList);
	//
	// biometricExceptionController.addExceptionToRegistration(biometricExceptionDTOs);
	//
	// }

	public List<String> getBioAttributesBySubType(String subType) {
		List<String> bioAttributes = new ArrayList<String>();
		if (subType != null) {
			bioAttributes = getAttributesByTypeAndSubType(RegistrationConstants.BIOMETRICS_TYPE, subType);
		}
		return bioAttributes;
	}

	private List<String> getAttributesByTypeAndSubType(String type, String subType) {
		List<String> bioAttributes = new LinkedList<>();
		if (type != null && subType != null) {
			for (Map.Entry<String, UiSchemaDTO> entry : validations.getValidationMap().entrySet()) {
				if (type.equalsIgnoreCase(entry.getValue().getType())
						&& subType.equalsIgnoreCase(entry.getValue().getSubType())
						&& entry.getValue().getBioAttributes() != null) {
					bioAttributes.addAll(entry.getValue().getBioAttributes());
				}
			}
		}
		return bioAttributes;
	}

	/*
	 * protected boolean isAvailableInBioAttributes(List<String> constantAttributes)
	 * { boolean isAvailable = false; List<String> uiSchemaBioAttributes =
	 * getBioAttributesBySubType(RegistrationConstants.indBiometrics); // If bio
	 * Attribute not mentioned for bio attribute then disable if
	 * (uiSchemaBioAttributes == null || uiSchemaBioAttributes.isEmpty()) {
	 * isAvailable = false; } else {
	 * 
	 * for (String attribute : constantAttributes) {
	 * 
	 * // If bio attribute configured in UI Schema, then enable the pane if
	 * (uiSchemaBioAttributes.contains(attribute)) {
	 * 
	 * isAvailable = true; } }
	 * 
	 * }
	 * 
	 * return isAvailable; }
	 */

	protected List<String> getNonConfigBioAttributes(String uiSchemaSubType, List<String> constantAttributes) {

		if ((boolean) SessionContext.map().get(RegistrationConstants.ONBOARD_USER))
			return constantAttributes;

		List<String> nonConfigBiometrics = new LinkedList<>();

		// Get Bio Attributes
		List<String> uiAttributes = getBioAttributesBySubType(uiSchemaSubType);

		for (String attribute : constantAttributes) {
			if (!uiAttributes.contains(attribute)) {
				nonConfigBiometrics.add(attribute);
			}
		}
		return nonConfigBiometrics;
	}

	protected boolean isDemographicField(UiSchemaDTO schemaField) {
		return (schemaField.isInputRequired()
				&& !(PacketManagerConstants.BIOMETRICS_DATATYPE.equals(schemaField.getType())
						|| PacketManagerConstants.DOCUMENTS_DATATYPE.equals(schemaField.getType())));
	}

	/*
	 * protected List<String> getConstantConfigBioAttributes(String bioType) {
	 * 
	 * return bioType.equalsIgnoreCase(RegistrationUIConstants.RIGHT_SLAP) ?
	 * RegistrationConstants.rightHandUiAttributes :
	 * bioType.equalsIgnoreCase(RegistrationUIConstants.LEFT_SLAP) ?
	 * RegistrationConstants.leftHandUiAttributes :
	 * bioType.equalsIgnoreCase(RegistrationUIConstants.THUMBS) ?
	 * RegistrationConstants.twoThumbsUiAttributes :
	 * bioType.equalsIgnoreCase(RegistrationConstants.IRIS) ?
	 * RegistrationConstants.eyesUiAttributes :
	 * bioType.equalsIgnoreCase(RegistrationConstants.FACE) ?
	 * Arrays.asList(RegistrationConstants.FACE) : null; }
	 */

	/*
	 * protected List<String> getConfigBioAttributes(List<String>
	 * constantAttributes) {
	 * 
	 * // Get Bio Attributes List<String> uiAttributes =
	 * getSchemaFieldBioAttributes(RegistrationConstants.indBiometrics);
	 * 
	 * return
	 * constantAttributes.stream().filter(uiAttributes::contains).collect(Collectors
	 * .toList());
	 * 
	 * 
	 * }
	 */

	protected List<String> getContainsAllElements(List<String> source, List<String> target) {
		if (target != null) {
			return source.stream().filter(target::contains).collect(Collectors.toList());
		}
		return new ArrayList<String>();
	}

	protected void helperMethodForComboBox(ComboBox<?> field, String fieldName, UiSchemaDTO schema, Label label,
			Label validationMessage, VBox vbox, String languageType) {

		String mandatoryAstrik = demographicDetailController.getMandatorySuffix(schema);
		if (languageType.equals(RegistrationConstants.LOCAL_LANGUAGE)) {
			label.setText(schema.getLabel().get(RegistrationConstants.SECONDARY) + mandatoryAstrik);
			field.setPromptText(label.getText());
			field.setDisable(true);
			putIntoLabelMap(fieldName + languageType, schema.getLabel().get(RegistrationConstants.SECONDARY));
		} else {
			label.setText(schema.getLabel().get(RegistrationConstants.PRIMARY) + mandatoryAstrik);
			field.setPromptText(label.getText());
			putIntoLabelMap(fieldName + languageType, schema.getLabel().get(RegistrationConstants.PRIMARY));
		}
		// vbox.setStyle("-fx-background-color:BLUE");
		vbox.setPrefWidth(500);
		vbox.setId(fieldName + RegistrationConstants.Parent);
		label.setId(fieldName + languageType + RegistrationConstants.LABEL);
		label.setVisible(false);
		label.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
		field.getStyleClass().add("demographicCombobox");
		validationMessage.setId(fieldName + languageType + RegistrationConstants.MESSAGE);
		validationMessage.getStyleClass().add(RegistrationConstants.DemoGraphicFieldMessageLabel);
		label.setPrefWidth(vbox.getPrefWidth());
		validationMessage.setPrefWidth(vbox.getPrefWidth());
		validationMessage.setVisible(false);
		vbox.setSpacing(5);

		vbox.getChildren().addAll(label, field, validationMessage);

//		if (applicationContext.getApplicationLanguage().equals(applicationContext.getLocalLanguage())
//				&& languageType.equals(RegistrationConstants.LOCAL_LANGUAGE)) {
//			vbox.setDisable(true);
//		}
	}

	protected void updateByAttempt(double qualityScore, Image streamImage, double thresholdScore,
			ImageView streamImagePane, Label qualityText, ProgressBar progressBar, Label progressQualityScore) {

		String qualityScoreLabelVal = getQualityScore(qualityScore);

		if (qualityScoreLabelVal != null) {
			// Set Stream image
			streamImagePane.setImage(streamImage);

			// Quality Label
			qualityText.setText(qualityScoreLabelVal);

			// Progress Bar
			progressBar.setProgress(qualityScore / 100);

			// Progress Bar Quality Score
			progressQualityScore.setText(qualityScoreLabelVal);

			if (qualityScore >= thresholdScore) {
				progressBar.getStyleClass().removeAll(RegistrationConstants.PROGRESS_BAR_RED);
				progressBar.getStyleClass().add(RegistrationConstants.PROGRESS_BAR_GREEN);
			} else {
				progressBar.getStyleClass().removeAll(RegistrationConstants.PROGRESS_BAR_GREEN);
				progressBar.getStyleClass().add(RegistrationConstants.PROGRESS_BAR_RED);
			}
		}
	}

	public Map<Entry<String, String>, Map<String, List<List<String>>>> getOnboardUserMap() {
		Map<Entry<String, String>, Map<String, List<List<String>>>> mapToProcess = new HashMap<>();

		Map<String, String> labels = new HashMap<>();
		labels.put("OPERATOR", RegistrationUIConstants.ONBOARD_USER_TITLE);

		Object value = ApplicationContext.map().get(RegistrationConstants.OPERATOR_ONBOARDING_BIO_ATTRIBUTES);
		List<String> attributes = (value != null) ? Arrays.asList(((String)value).split(",")) :
				new ArrayList<String>();
		//subMap.put(slabType, Arrays.asList(configBiometrics, nonConfigBiometrics));
		HashMap<String, List<List<String>>> subMap = new HashMap<String, List<List<String>>>();
		subMap.put(RegistrationConstants.FINGERPRINT_SLAB_LEFT,
				Arrays.asList(ListUtils.intersection(RegistrationConstants.leftHandUiAttributes, attributes),
			ListUtils.subtract(RegistrationConstants.leftHandUiAttributes, attributes)));
		subMap.put(RegistrationConstants.FINGERPRINT_SLAB_RIGHT,
				Arrays.asList(ListUtils.intersection(RegistrationConstants.rightHandUiAttributes, attributes),
						ListUtils.subtract(RegistrationConstants.rightHandUiAttributes, attributes)));
		subMap.put(RegistrationConstants.FINGERPRINT_SLAB_THUMBS,
				Arrays.asList(ListUtils.intersection(RegistrationConstants.twoThumbsUiAttributes, attributes),
				ListUtils.subtract(RegistrationConstants.twoThumbsUiAttributes, attributes)));
		subMap.put(RegistrationConstants.IRIS_DOUBLE,
				Arrays.asList(ListUtils.intersection(RegistrationConstants.eyesUiAttributes, attributes),
				ListUtils.subtract(RegistrationConstants.eyesUiAttributes, attributes)));
		subMap.put(RegistrationConstants.FACE, Arrays.asList(ListUtils.intersection(RegistrationConstants.faceUiAttributes, attributes),
						ListUtils.subtract(RegistrationConstants.faceUiAttributes, attributes)));

		for (Entry<String, String> entry : labels.entrySet()) {
			mapToProcess.put(entry, subMap);
		}
		return mapToProcess;
	}

	protected List<UiSchemaDTO> fetchByGroup(String group) {
		return validation.getValidationMap().values().stream()
				.filter(schemaDto -> schemaDto.getGroup() != null && schemaDto.getGroup().equalsIgnoreCase(group))
				.collect(Collectors.toList());
	}

	protected String getCssName() {
		return cssName;
	}

	protected String getLocalZoneTime(String time) {
		try {
			String formattedTime = Timestamp.valueOf(time).toLocalDateTime()
					.format(DateTimeFormatter.ofPattern(RegistrationConstants.UTC_PATTERN));
			LocalDateTime dateTime = DateUtils.parseUTCToLocalDateTime(formattedTime);
			return dateTime
					.format(DateTimeFormatter.ofPattern(RegistrationConstants.ONBOARD_LAST_BIOMETRIC_UPDTAE_FORMAT));
		} catch (RuntimeException exception) {
			LOGGER.error("REGISTRATION - ALERT - BASE_CONTROLLER", APPLICATION_NAME, APPLICATION_ID,
					ExceptionUtils.getStackTrace(exception));
			return time + RegistrationConstants.UTC_APPENDER;
		}

	}


	public boolean isAppLangAndLocalLangSame() {

		return applicationContext.getLocalLanguage() != null && applicationContext.getApplicationLanguage().equals(applicationContext.getLocalLanguage());
	}

	public boolean isLocalLanguageAvailable() {

		return applicationContext.getLocalLanguage() != null && !applicationContext.getLocalLanguage().isEmpty();
	}

	public boolean proceedOnAction(String job) {
		if (isPrimaryOrSecondaryLanguageEmpty()) {
			generateAlert(RegistrationConstants.ERROR,
					RegistrationUIConstants.UNABLE_LOAD_LOGIN_SCREEN_LANGUAGE_NOT_SET);
			return false;
		}

		if (!RegistrationAppHealthCheckUtil.isNetworkAvailable()) {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.NO_INTERNET_CONNECTION);
			return false;
		}

		if (!authTokenUtilService.hasAnyValidToken()) {
			generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.USER_RELOGIN_REQUIRED);
			return false;
		}

		try {
			switch (job) {
				case "MS" :
					baseService.proceedWithMasterAndKeySync(null);
					break;
				case "PS":
					baseService.proceedWithPacketSync();
					break;
				case "RM":
					baseService.proceedWithMachineCenterRemap();
					break;
				case "OU" :
					baseService.proceedWithOperatorOnboard();
					break;
				default:
					baseService.proceedWithMasterAndKeySync(job);
					break;
			}
		} catch (PreConditionCheckException ex){
			generateAlert(RegistrationConstants.ERROR,
					ApplicationContext.applicationMessagesBundle().containsKey(ex.getErrorCode()) ?
							ApplicationContext.applicationMessagesBundle().getString(ex.getErrorCode()) :
							ex.getErrorCode());
			return false;
		}
		return true;
	}

	public boolean proceedOnRegistrationAction() {
		if (isPrimaryOrSecondaryLanguageEmpty()) {
			generateAlert(RegistrationConstants.ERROR,
					RegistrationUIConstants.UNABLE_LOAD_LOGIN_SCREEN_LANGUAGE_NOT_SET);
			return false;
		}

		try {
			baseService.proceedWithRegistration();
		} catch (PreConditionCheckException ex){
			generateAlert(RegistrationConstants.ERROR,
					ApplicationContext.applicationMessagesBundle().containsKey(ex.getErrorCode()) ?
							ApplicationContext.applicationMessagesBundle().getString(ex.getErrorCode()) :
							ex.getErrorCode());
			return false;
		}
		return true;
	}

	public boolean proceedOnReRegistrationAction() {
		if (isPrimaryOrSecondaryLanguageEmpty()) {
			generateAlert(RegistrationConstants.ERROR,
					RegistrationUIConstants.UNABLE_LOAD_LOGIN_SCREEN_LANGUAGE_NOT_SET);
			return false;
		}

		try {
			baseService.proceedWithReRegistration();
		} catch (PreConditionCheckException ex){
			generateAlert(RegistrationConstants.ERROR,
					ApplicationContext.applicationMessagesBundle().containsKey(ex.getErrorCode()) ?
							ApplicationContext.applicationMessagesBundle().getString(ex.getErrorCode()) :
							ex.getErrorCode());
			return false;
		}
		return true;
	}

	public static Image getImage(BufferedImage bufferedImage) {
		WritableImage wr = null;
		if (bufferedImage != null) {
			wr = new WritableImage(bufferedImage.getWidth(), bufferedImage.getHeight());
			PixelWriter pw = wr.getPixelWriter();
			for (int x = 0; x < bufferedImage.getWidth(); x++) {
				for (int y = 0; y < bufferedImage.getHeight(); y++) {
					pw.setArgb(x, y, bufferedImage.getRGB(x, y));
				}
			}
		}
		return wr;
	}
}
