package io.mosip.registration.controller.reg;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_ID;
import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ScrollEvent;
import org.assertj.core.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.github.sarxos.webcam.Webcam;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.pdfgenerator.spi.PDFGenerator;
import io.mosip.kernel.core.util.StringUtils;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.AuditEvent;
import io.mosip.registration.constants.AuditReferenceIdTypes;
import io.mosip.registration.constants.Components;
import io.mosip.registration.constants.LoggerConstants;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.context.SessionContext;
import io.mosip.registration.controller.BaseController;
import io.mosip.registration.controller.FXUtils;
import io.mosip.registration.controller.device.BiometricsController;
import io.mosip.registration.controller.device.ScanPopUpViewController;
import io.mosip.registration.device.scanner.dto.ScanDevice;
import io.mosip.registration.device.webcam.impl.WebcamSarxosServiceImpl;
import io.mosip.registration.dto.UiSchemaDTO;
import io.mosip.registration.dto.mastersync.DocumentCategoryDto;
import io.mosip.registration.dto.packetmanager.DocumentDto;
import io.mosip.registration.exception.RegBaseCheckedException;
import io.mosip.registration.service.doc.category.DocumentCategoryService;
import io.mosip.registration.service.sync.MasterSyncService;
import io.mosip.registration.util.scan.DocumentScanFacade;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * {@code DocumentScanController} is to handle the screen of the Demographic
 * document section details
 *
 * @author M1045980
 * @since 1.0.0
 */
@Controller
public class DocumentScanController extends BaseController {

	private static final Logger LOGGER = AppConfig.getLogger(DocumentScanController.class);

	@Autowired
	private RegistrationController registrationController;

	private String selectedDocument;

	private ComboBox<DocumentCategoryDto> selectedComboBox;

	private VBox selectedDocVBox;

	private Map<String, ComboBox<DocumentCategoryDto>> documentComboBoxes = new HashMap<>();

	private Map<String, VBox> documentVBoxes = new HashMap<>();

	@Autowired
	private ScanPopUpViewController scanPopUpViewController;

	@Autowired
	private DocumentScanFacade documentScanFacade;

	@Autowired
	private PDFGenerator pdfGenerator;

	@FXML
	protected GridPane documentScan;

	@FXML
	private GridPane documentPane;

	@FXML
	protected ImageView docPreviewImgView;

	@FXML
	protected Label docPreviewNext;

	@FXML
	protected Label docPreviewPrev;

	@FXML
	protected Label docPageNumber;

	@FXML
	protected Label docPreviewLabel;
	@FXML
	public GridPane documentScanPane;

	@FXML
	private VBox docScanVbox;

	private List<BufferedImage> scannedPages;

	@Autowired
	private BiometricsController guardianBiometricsController;

	@Autowired
	private MasterSyncService masterSyncService;

	@Autowired
	private DocumentCategoryService documentCategoryService;

	@Autowired
	private DemographicDetailController demographicDetailController;

	public List<BufferedImage> getDocPages() {
		return docPages;
	}

	private List<BufferedImage> docPages;

	@FXML
	private Label registrationNavlabel;

	@FXML
	private Button continueBtn;
	@FXML
	private Button backBtn;
	@FXML
	private ImageView backImageView;
	@FXML
	private Label biometricExceptionReq;
	@FXML
	private Group docPreviewImgGroup;

	@Autowired
	private Validations validation;

	@Autowired
	private WebcamSarxosServiceImpl webcamSarxosServiceImpl;

	private String selectedScanDeviceName;

	private ImageView imageView;
	private Stage primaryStage;

	private String cropDocumentKey;

	/*private Webcam webcam;

	public Webcam getWebcam() {
		return webcam;
	}

	public void setWebcam(Webcam webcam) {
		this.webcam = webcam;
	}*/

	final DoubleProperty zoomProperty = new SimpleDoubleProperty(200);

	@FXML
	private ScrollPane docPreviewScrollPane;

	/*
	 * (non-Javadoc)
	 *
	 * @see javafx.fxml.Initializable#initialize()
	 */
	@FXML
	private void initialize() {
		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Entering the DOCUMENT_SCAN_CONTROLLER");

		Image backInWhite = new Image(getClass().getResourceAsStream(RegistrationConstants.BACK_FOCUSED));
		Image backImage = new Image(getClass().getResourceAsStream(RegistrationConstants.BACK));
		backBtn.hoverProperty().addListener((ov, oldValue, newValue) -> {
			if (newValue) {
				backImageView.setImage(backInWhite);
			} else {
				backImageView.setImage(backImage);
			}
		});

		try {

			if (getRegistrationDTOFromSession() != null
					&& getRegistrationDTOFromSession().getSelectionListDTO() != null) {

				registrationNavlabel.setText(ApplicationContext.applicationLanguageBundle()
						.getString(RegistrationConstants.UIN_UPDATE_UINUPDATENAVLBL));
			}

			if (getRegistrationDTOFromSession() != null
					&& getRegistrationDTOFromSession().getRegistrationMetaDataDTO().getRegistrationCategory() != null
					&& getRegistrationDTOFromSession().getRegistrationMetaDataDTO().getRegistrationCategory()
							.equals(RegistrationConstants.PACKET_TYPE_LOST)) {

				registrationNavlabel.setText(
						ApplicationContext.applicationLanguageBundle().getString(RegistrationConstants.LOSTUINLBL));
				docScanVbox.setDisable(true);
				continueBtn.setDisable(false);
			} else {
				continueBtn.setDisable(true);
			}

		} catch (RuntimeException exception) {
			LOGGER.error("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID,
					exception.getMessage() + ExceptionUtils.getStackTrace(exception));
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.UNABLE_LOAD_REG_PAGE);
		}

		zoomProperty.addListener(new InvalidationListener() {
			@Override
			public void invalidated(Observable arg0) {
				docPreviewImgView.setFitWidth(zoomProperty.get() * 4);
				docPreviewImgView.setFitHeight(zoomProperty.get() * 3);
			}
		});

		docPreviewScrollPane.addEventFilter(ScrollEvent.ANY, new EventHandler<ScrollEvent>() {
			@Override
			public void handle(ScrollEvent event) {
				if (event.getDeltaY() > 0) {
					zoomProperty.set(zoomProperty.get() * 1.1);
				} else if (event.getDeltaY() < 0) {
					zoomProperty.set(zoomProperty.get() / 1.1);
				}
			}
		});
	}

	/**
	 * To populate the document categories
	 */
	protected <T> void populateDocumentCategories() {

		/* clearing all the previously added fields */
		docScanVbox.getChildren().clear();
		documentComboBoxes.clear();
		documentVBoxes.clear();
		initializePreviewSection();

		docScanVbox.setSpacing(5);

		prepareDocumentScanSection(getDocId());

		/*
		 * populate the documents for edit if its already present or fetched from pre
		 * reg
		 */
		Map<String, DocumentDto> documentsMap = getDocumentsMapFromSession();
		if (documentsMap != null && !documentsMap.isEmpty() && !documentVBoxes.isEmpty()) {
			Set<String> docCategoryKeys = documentVBoxes.keySet();
			List<String> docCategoryList = new ArrayList<String>();
			docCategoryList.addAll(docCategoryKeys);
			if (documentsMap.containsKey("proofOfException")) {
				docCategoryList.add("proofOfException");
			}
			documentsMap.keySet().retainAll(docCategoryList);
			for (String docCategoryKey : docCategoryKeys) {

				DocumentDto documentDetailsDTO = documentsMap.get(docCategoryKey);

				if (documentDetailsDTO != null) {
					addDocumentsToScreen(documentDetailsDTO.getValue(), documentDetailsDTO.getFormat(),
							documentVBoxes.get(docCategoryKey));

					addDocumentRefNumber(documentDetailsDTO.getRefNumber(),
							documentVBoxes.get(documentDetailsDTO.getCategory() + "RefNumVBox"));

					FXUtils.getInstance().selectComboBoxValue(documentComboBoxes.get(docCategoryKey),
							documentDetailsDTO.getValue().substring(
									documentDetailsDTO.getValue().lastIndexOf(RegistrationConstants.UNDER_SCORE) + 1));
				}
			}
		} else if (documentVBoxes.isEmpty() && documentsMap != null) {
			documentsMap.clear();
		}

		if (getRegistrationDTOFromSession().getSelectionListDTO() != null && RegistrationConstants.DISABLE
				.equalsIgnoreCase(getValueFromApplicationContext(RegistrationConstants.DOC_DISABLE_FLAG))) {
			documentPane.setVisible(false);
		}
		validateDocumentsPane();
	}

	private void addDocumentRefNumber(String refNumber, VBox vBox) {
		if (refNumber != null && vBox != null) {
			GridPane gridPane = (GridPane) vBox.getChildren().get(0);
			TextField textField = (TextField) gridPane.getChildren().get(0);
			textField.setText(refNumber);
		}
	}

	private Map<String, DocumentDto> getDocumentsMapFromSession() {
		return getRegistrationDTOFromSession().getDocuments();
	}

	/**
	 * To prepare the document section
	 */
	@SuppressWarnings("unchecked")
	private <T> void prepareDocumentScanSection(List<UiSchemaDTO> documentFields) {

		LOGGER.debug(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Preparing document section");

		if (RegistrationConstants.YES
				.equalsIgnoreCase(getValueFromApplicationContext(RegistrationConstants.DOC_SCANNER_ENABLED))) {

			LOGGER.debug(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Found Document scanner with device enabled");

			HBox scannerHbox = new HBox();
			scannerHbox.setStyle("-fx-padding: 10;");
			Label selectScannerLabel = new Label();
			selectScannerLabel.setWrapText(true);
			selectScannerLabel.setText(RegistrationUIConstants.SELECTED_SCANNER);
			selectScannerLabel.setPrefWidth(180);
			selectScannerLabel.getStyleClass().add(RegistrationConstants.BUTTONS_LABEL);
			ComboBox<String> scannerComboBox = new ComboBox<>();
			scannerComboBox.setPrefWidth(docScanVbox.getWidth() / 2);
			scannerComboBox.getStyleClass().add(RegistrationConstants.DOC_COMBO_BOX);
			scannerComboBox.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
				selectedScanDeviceName = newValue;
			});

			LOGGER.debug(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Setting Document scanner factory");

			if (documentScanFacade.setScannerFactory()) {

				LOGGER.debug(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID, "Finding the devices used to scan the documents");

				List<ScanDevice> scannerDevices = documentScanFacade.getDevices();

				if (scannerDevices.isEmpty()) {
					LOGGER.debug(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, APPLICATION_NAME,
							RegistrationConstants.APPLICATION_ID,
							"Not Found the devices used to scan the document : " + scannerDevices);
					scannerComboBox.setPromptText(RegistrationUIConstants.NO_SCANNER_FOUND);
					scannerComboBox.setDisable(true);
				} else {
					LOGGER.debug(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, APPLICATION_NAME,
							RegistrationConstants.APPLICATION_ID,
							"Found the devices used to scan the document : " + scannerDevices);
					List<String> deviceNames = scannerDevices.stream().map(device -> device.getName())
							.collect(Collectors.toList());
					LOGGER.debug(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, APPLICATION_NAME,
							RegistrationConstants.APPLICATION_ID,
							"Found the devices used to scan the document : " + scannerDevices);
					scannerComboBox.getItems().addAll(deviceNames);
					scannerComboBox.getSelectionModel().selectFirst();
					selectedScanDeviceName = scannerComboBox.getSelectionModel().getSelectedItem();
				}
			}
			scannerHbox.getChildren().addAll(selectScannerLabel, scannerComboBox);
			docScanVbox.getChildren().add(scannerHbox);
		}

		/* show the scan doc info label for format and size */
		Label fileSizeInfoLabel = new Label();
		fileSizeInfoLabel.setWrapText(true);
		int sizeMB = (int) Math.ceil(Integer.parseInt(getValueFromApplicationContext(RegistrationConstants.DOC_SIZE))/(1024*1024));
		fileSizeInfoLabel.setText(RegistrationUIConstants.SCAN_DOC_INFO.replace("1", Integer.toString(sizeMB)));
		docScanVbox.getChildren().add(fileSizeInfoLabel);

		for (UiSchemaDTO documentCategory : documentFields) {

			String docCategoryCode = documentCategory.getSubType();

			String docCategoryName = getDocName(documentCategory);

			List<DocumentCategoryDto> documentCategoryDtos = null;

			try {
				documentCategoryDtos = masterSyncService.getDocumentCategories(docCategoryCode,
						ApplicationContext.applicationLanguage());
			} catch (RuntimeException runtimeException) {
				LOGGER.error("REGISTRATION - LOADING LIST OF DOCUMENTS FAILED ", APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID,
						runtimeException.getMessage() + ExceptionUtils.getStackTrace(runtimeException));
			} catch (RegBaseCheckedException checkedException) {
				LOGGER.error("REGISTRATION - LOADING LIST OF DOCUMENTS FAILED ", APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID,
						checkedException.getMessage() + ExceptionUtils.getStackTrace(checkedException));
			}

			if (documentCategoryDtos != null && !documentCategoryDtos.isEmpty()) {
				HBox hBox = new HBox();
				ComboBox<DocumentCategoryDto> comboBox = new ComboBox<>();
				comboBox.setPrefWidth(docScanVbox.getWidth() / 2);
				comboBox.setId(documentCategory.getId());

				/*
				 * comboBox.valueProperty().addListener((v, oldValue, newValue) -> {
				 * validateDocumentsPane(); });
				 */
				ImageView indicatorImage = new ImageView(
						new Image(this.getClass().getResourceAsStream(RegistrationConstants.CLOSE_IMAGE_PATH), 15, 15,
								true, true));
				comboBox.setPromptText(docCategoryName);

				comboBox.getStyleClass().add(RegistrationConstants.DOC_COMBO_BOX);
				Label documentLabel = new Label(docCategoryName);
				documentLabel.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
				documentLabel.setPrefWidth(docScanVbox.getWidth() / 2);
				documentLabel.setVisible(false);
				comboBox.getSelectionModel().selectedItemProperty().addListener((options, oldValue, newValue) -> {
					documentLabel.setVisible(true);
				});
				StringConverter<T> uiRenderForComboBox = FXUtils.getInstance().getStringConverterForComboBox();
				comboBox.setConverter((StringConverter<DocumentCategoryDto>) uiRenderForComboBox);
				if (applicationContext.isPrimaryLanguageRightToLeft()) {
					comboBox.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
					documentLabel.setAlignment(Pos.CENTER_RIGHT);
				}

				/*
				 * adding all the dynamically created combo boxes in a map inorder to show it in
				 * the edit page
				 */
				documentComboBoxes.put(documentCategory.getId(), comboBox);
				putIntoLabelMap(documentCategory.getId(), docCategoryName);
				VBox documentVBox = new VBox();
				documentVBox.getStyleClass().add(RegistrationConstants.SCAN_VBOX);
				documentVBox.setId(documentCategory.getId());

				documentVBoxes.put(documentCategory.getId(), documentVBox);

				VBox refNumVBox = new VBox();
				refNumVBox.setId(docCategoryCode + "RefNumVBox");
				refNumVBox.getStyleClass().add(RegistrationConstants.SCAN_VBOX);
				TextField documentNumberTxtField = new TextField();
				documentNumberTxtField.setId(docCategoryCode + "RefNum");
				documentNumberTxtField.setPromptText(RegistrationUIConstants.REF_NUMBER);
				documentNumberTxtField.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD);
				documentNumberTxtField.setStyle("-fx-font-size:13");
				documentNumberTxtField.setMinWidth(100);

				// numberOfDocs.setPrefWidth(40);

				documentVBoxes.put(refNumVBox.getId(), refNumVBox);

				GridPane gridPane = new GridPane();

				gridPane.setId(docCategoryCode + "RefNumGridPane");
				gridPane.setVgap(10);
				gridPane.setHgap(10);
				gridPane.setPrefWidth(80);
				gridPane.add(documentNumberTxtField, 1, 0);

				refNumVBox.getChildren().add(gridPane);

				Label refNumLabel = new Label(RegistrationUIConstants.REF_NUMBER);
				refNumLabel.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_FIELD_LABEL);
				// pagesLabel.setPrefWidth(docScanVbox.getWidth() / 2);
				refNumLabel.setVisible(false);
				refNumLabel.setId(docCategoryCode + "RefNumLabel");
				FXUtils.getInstance().onTypeFocusUnfocusListener((Pane) docScanVbox.getParent(),
						documentNumberTxtField);

				if (applicationContext.isPrimaryLanguageRightToLeft()) {
					comboBox.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
					documentLabel.setAlignment(Pos.CENTER_RIGHT);
					documentNumberTxtField.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
					refNumLabel.setAlignment(Pos.CENTER_RIGHT);
				}

				Button scanButton = new Button();
				scanButton.setText(RegistrationUIConstants.SCAN);
				scanButton.setId(docCategoryCode);
				scanButton.getStyleClass().add(RegistrationConstants.DOCUMENT_CONTENT_BUTTON);
				scanButton.setGraphic(new ImageView(new Image(
						this.getClass().getResourceAsStream(RegistrationConstants.SCAN), 12, 12, true, true)));
				scanButton.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent event) {

						LOGGER.debug("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
								RegistrationConstants.APPLICATION_ID,
								"Document Action listener started for scan" + ((Button) event.getSource()).getId());

						AuditEvent auditEvent = null;
						try {
							auditEvent = AuditEvent
									.valueOf(String.format("REG_DOC_%S_SCAN", ((Button) event.getSource()).getId()));
						} catch (Exception exception) {

							LOGGER.error("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
									RegistrationConstants.APPLICATION_ID,
									"Unable to find audit event for button : " + ((Button) event.getSource()).getId());

							auditEvent = AuditEvent.REG_DOC_SCAN;

						}
						auditFactory.audit(auditEvent, Components.REG_DOCUMENTS, SessionContext.userId(),
								AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

						Button clickedBtn = (Button) event.getSource();
						clickedBtn.getId();
						scanDocument(comboBox, documentVBox, documentCategory.getSubType(),
								RegistrationUIConstants.PLEASE_SELECT + RegistrationConstants.SPACE
										+ documentCategory.getSubType() + " " + RegistrationUIConstants.DOCUMENT);

						LOGGER.debug("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
								RegistrationConstants.APPLICATION_ID,
								"Document Action listener completed for scan" + ((Button) event.getSource()).getId());

					}
				});
				scanButton.hoverProperty().addListener((ov, oldValue, newValue) -> {
					if (newValue) {
						scanButton.setGraphic(new ImageView(
								new Image(this.getClass().getResourceAsStream(RegistrationConstants.SCAN_FOCUSED), 12,
										12, true, true)));
					} else {
						scanButton.setGraphic(new ImageView(new Image(
								this.getClass().getResourceAsStream(RegistrationConstants.SCAN), 12, 12, true, true)));
					}
				});

				GridPane scanButtonGridPane = new GridPane();
//				gridPane.setId(docCategoryCode + "RefNumGridPane");
//				scanButtonGridPane.setVgap(10);
//				scanButtonGridPane.setHgap(10);
				scanButtonGridPane.setPrefWidth(80);
				scanButtonGridPane.add(scanButton, 0, 0);
				hBox.getChildren().addAll(new VBox(new Label(), indicatorImage), comboBox, refNumVBox, documentVBox,
						scanButtonGridPane);

				GridPane labelGridPane = new GridPane();

				labelGridPane.setHgap(-20);
				labelGridPane.add(documentLabel, 0, 0);
				labelGridPane.add(refNumLabel, 1, 0);

				docScanVbox.getChildren().addAll(new HBox(labelGridPane), hBox);
				hBox.setId(documentCategory.getSubType()+"hBox");
				documentLabel.setId(documentCategory.getSubType() + RegistrationConstants.LABEL);
				comboBox.getItems().addAll(documentCategoryDtos);

			}
		}

	}

	private String getDocName(UiSchemaDTO documentCategory) {
		return documentCategory.getLabel() != null ? documentCategory.getLabel().get("primary")
				: documentCategory.getDescription();
	}

	private String getDocName(String docId) {

		Optional<Entry<String, UiSchemaDTO>> optional = validation.getValidationMap().entrySet().stream()
				.filter(map -> map.getValue().getId().equalsIgnoreCase(docId)).findFirst();

		if (optional.isPresent()) {
			return getDocName(optional.get().getValue());
		}

		return null;

	}

	/**
	 * This method scans and uploads documents
	 */
	private void scanDocument(ComboBox<DocumentCategoryDto> documents, VBox vboxElement, String document,
			String errorMessage) {

		if (documents.getValue() == null) {
			LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Select atleast one document for scan");

			generateAlert(RegistrationConstants.ERROR, errorMessage);
			documents.requestFocus();
		}
		else {
			LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Displaying Scan window to scan Documents");
			// documents.getValue().setCode(document);
			selectedDocument = document;
			selectedComboBox = documents;
			selectedDocVBox = vboxElement;
			scanWindow();
		}
	}

	/**
	 * This method will display Scan window to scan and upload documents
	 */
	private void scanWindow() {

		Webcam webcam = webcamSarxosServiceImpl.getWebCam(selectedScanDeviceName);
		if ((selectedScanDeviceName == null || selectedScanDeviceName.isEmpty()) || webcam == null) {
			documentScanFacade.setStubScannerFactory();
		} else {
			documentScanFacade.setScannerFactory();
		}

		if (getRegistrationDTOFromSession().getDocuments() != null) {
			DocumentDto documentDto = getRegistrationDTOFromSession().getDocuments().get(selectedComboBox.getId());

			try {

				if (documentDto != null) {
					
					
					if(RegistrationConstants.PDF.equalsIgnoreCase(documentDto.getFormat())) {

						setScannedPages(documentScanFacade.pdfToImages(documentDto.getDocument()));
					} else {
						 InputStream is = new ByteArrayInputStream(documentDto.getDocument());
		                   BufferedImage newBi = ImageIO.read(is);
		        
		                   List<BufferedImage> list = new LinkedList<>();
		                   list.add(newBi);
		                   setScannedPages(list);		        
					}
				}
			} catch (IOException exception) {
				LOGGER.error("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID,
						exception.getMessage() + ExceptionUtils.getStackTrace(exception));

			}

		}

		scanPopUpViewController.setDocumentScan(true);
		scanPopUpViewController.init(this, RegistrationUIConstants.SCAN_DOC_TITLE, webcam);
		if (webcam != null) {
			documentScanFacade.setStubScannerFactory();
			//scanPopUpViewController.startStream(webcam);
		}
		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Scan window displayed to scan and upload documents");
	}

	/*public void startStream(BaseController baseController) {
		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Searching for webcams");

		List<Webcam> webcams = webcamSarxosServiceImpl.getWebCams();

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Found webcams: " + webcams);

		if (webcams != null && !webcams.isEmpty()) {
			LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Initializing scan window to capture Exception photo");

			scanPopUpViewController.init(baseController, RegistrationUIConstants.SCAN_DOC_TITLE, webcams.get(0));
			//webcam = webcams.get(0);

			LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Checking webcam connectivity");

			if (!webcamSarxosServiceImpl.isWebcamConnected(webcams.get(0))) {
				LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID, "Opening webcam");

				scanPopUpViewController.startStream(webcams.get(0));
				// Enable Auto-Logout
				SessionContext.setAutoLogout(false);
				LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID, "Webcam stream started");
			} else {
				generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.NO_DEVICE_FOUND);
				scanPopUpViewController.setDefaultImageGridPaneVisibility();

				LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID, "No webcam found");
			}
		} else {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.NO_DEVICE_FOUND);
			scanPopUpViewController.setDefaultImageGridPaneVisibility();
			return;
		}
	}*/

	/**
	 * This method will allow to scan and upload documents
	 */
	@Override
	public void scan(Stage popupStage) {
		try {

			if (RegistrationConstants.YES
					.equalsIgnoreCase(getValueFromApplicationContext(RegistrationConstants.DOC_SCANNER_ENABLED))) {
				scanFromScanner();
			} else {
				scanFromStubbed(popupStage);
			}
		} catch (IOException ioException) {
			LOGGER.error(LoggerConstants.LOG_REG_REGISTRATION_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					String.format("%s -> Exception while scanning documents for registration  %s -> %s",
							RegistrationConstants.USER_REG_DOC_SCAN_UPLOAD_EXP, ioException.getMessage(),
							ExceptionUtils.getStackTrace(ioException)));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.SCAN_DOCUMENT_ERROR);
		} catch (RuntimeException runtimeException) {
			LOGGER.error(LoggerConstants.LOG_REG_REGISTRATION_CONTROLLER, APPLICATION_NAME, APPLICATION_ID,
					String.format("%s -> Exception while scanning documents for registration  %s",
							RegistrationConstants.USER_REG_DOC_SCAN_UPLOAD_EXP, runtimeException.getMessage())
							+ ExceptionUtils.getStackTrace(runtimeException));

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.SCAN_DOCUMENT_ERROR);
		}

	}

	/**
	 * This method will get the stubbed data for the scan
	 */
	private void scanFromStubbed(Stage popupStage) throws IOException {
		documentScanFacade.setStubScannerFactory();
		BufferedImage bufferedImage = documentScanFacade.getScannedDocument();
		if (bufferedImage == null) {
			LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "captured buffered image was null");
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.SCAN_DOCUMENT_ERROR);
			return;
		}

		if (scannedPages == null) {
			scannedPages = new ArrayList<>();
		}
		scannedPages.add(bufferedImage);
		/* show the scanned page in the preview */
		scanPopUpViewController.getScanImage().setImage(getImage(bufferedImage));
		scanPopUpViewController.getScanningMsg().setVisible(false);
		generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.DOC_CAPTURE_SUCCESS);
	}


	/**
	 * This method is to scan from the scanner
	 */
	private void scanFromScanner() throws IOException {
		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Scanning from scanner");

		/* setting the scanner factory */
		if (!documentScanFacade.setScannerFactory()) {
			LOGGER.error(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Setting scanner factory failed");

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.SCAN_DOCUMENT_CONNECTION_ERR);
			return;
		}
		if (selectedScanDeviceName == null || selectedScanDeviceName.isEmpty()) {
			LOGGER.error(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Selected device name was empty");

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.SCAN_DOCUMENT_CONNECTION_ERR);
			return;
		}

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Setting scan message name visible");

		scanPopUpViewController.getScanningMsg().setVisible(true);

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "checking for POE value");

		BufferedImage bufferedImage = documentScanFacade.getScannedDocumentFromScanner(selectedScanDeviceName);

		if (bufferedImage == null) {
			LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "captured buffered image was null");

			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.SCAN_DOCUMENT_ERROR);
			return;
		}
		if (scannedPages == null) {
			scannedPages = new ArrayList<>();
		}
		scannedPages.add(bufferedImage);

		/* show the scanned page in the preview */
		scanPopUpViewController.getImageGroup().getChildren().clear();
		scanPopUpViewController.getImageGroup().getChildren().add(new ImageView(getImage(bufferedImage)));
		//scanPopUpViewController.getScanImage().setImage(getImage(bufferedImage));
		scanPopUpViewController.getScanningMsg().setVisible(false);
		generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.DOC_CAPTURE_SUCCESS);
	}


	/**
	 * This method is to attach the document to the screen
	 */
	public void attachScannedDocument(Stage popupStage) throws IOException {

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Converting byte array to image");
		String documentSize = getValueFromApplicationContext(RegistrationConstants.DOC_SIZE);
		int docSize = (int) Math.ceil(Integer.parseInt(documentSize) / (1024 * 1024));
		if (scannedPages == null || scannedPages.isEmpty()) {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.SCAN_DOCUMENT_EMPTY);
			return;
		}
		byte[] byteArray = getScannedPagesToBytes(scannedPages);

		if (byteArray == null) {
			generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.SCAN_DOCUMENT_CONVERTION_ERR);
			return;
		}

		if (docSize <= (byteArray.length / (1024 * 1024))) {
			scannedPages.clear();
			generateAlert(RegistrationConstants.ERROR,
					RegistrationUIConstants.SCAN_DOC_SIZE.replace("1", Integer.toString(docSize)));
		} else {
			if (selectedDocument != null) {
				LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID, "Adding documents to Screen");

				attachDocuments(selectedComboBox.getValue(), selectedDocVBox, byteArray, false);

				LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID, "Documents added successfully");
			}
		}
	}

	private byte[] getScannedPagesToBytes(List<BufferedImage> scannedPages) throws IOException {

		byte[] byteArray = null;
		if ("pdf".equalsIgnoreCase(getValueFromApplicationContext(RegistrationConstants.DOC_TYPE))) {
			byteArray = documentScanFacade.asPDF(scannedPages);
		} else {
			byteArray = documentScanFacade.asImage(scannedPages);
		}
		return byteArray;
	}

	/**
	 * This method will add Hyperlink and Image for scanned documents
	 *
	 * @throws IOException
	 */
	private void attachDocuments(DocumentCategoryDto document, VBox vboxElement, byte[] byteArray, boolean isStubbed)
			throws IOException {

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Attaching documemnts to Pane");

		DocumentDto documentDto = getRegistrationDTOFromSession().getDocuments().get(selectedComboBox.getId());

		if (documentDto == null) {
			documentDto = new DocumentDto();
			documentDto.setDocument(byteArray);
			documentDto.setType(document.getCode());

			String docType = getValueFromApplicationContext(RegistrationConstants.DOC_TYPE);
			if (isStubbed) {
				docType = RegistrationConstants.SCANNER_IMG_TYPE;
			}

			documentDto.setFormat(docType);
			documentDto.setCategory(selectedDocument);
			documentDto.setOwner("Applicant");
			documentDto.setValue(selectedDocument.concat(RegistrationConstants.UNDER_SCORE).concat(document.getCode()));
		} else {
			List<BufferedImage> capBufferedImages = documentScanFacade.pdfToImages(byteArray);
			documentDto.setDocument(getScannedPagesToBytes(capBufferedImages));
		}

		HBox hBox = (HBox) vboxElement.getParent();
		for (Node node : hBox.getChildren()) {
			if (node.getId() != null && node.getId().contains("RefNumVBox")) {
				VBox vbox = (VBox) node;
				GridPane gridPane = (GridPane) vbox.getChildren().get(0);
				TextField textField = (TextField) gridPane.getChildren().get(0);
				if (textField.getText() != null && !textField.getText().isEmpty()) {

					documentDto.setRefNumber(textField.getText());
				}
			}
		}
		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Set DocumentDetailsDTO to RegistrationDTO");
		addDocumentsToScreen(documentDto.getValue(), documentDto.getFormat(), vboxElement);

		selectedComboBox.getValue().setScanned(true);

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Validating document screen");

		validateDocumentsPane();

		generateAlert(RegistrationConstants.ALERT_INFORMATION, RegistrationUIConstants.SCAN_DOC_SUCCESS);

		LOGGER.info("Adding document- {} to session with size : {}", documentDto.getValue(), documentDto.getDocument() != null ?
				documentDto.getDocument().length : 0);

		getRegistrationDTOFromSession().addDocument(selectedComboBox.getId(), documentDto);
	}

	/**
	 * This method will add document to the screen
	 */
	private void addDocumentsToScreen(String document, String documentFormat, VBox vboxElement) {

		GridPane gridPane = new GridPane();
		gridPane.setId(document);
		gridPane.setVgap(15);
		gridPane.setHgap(15);
		gridPane.add(createHyperLink(document.concat(RegistrationConstants.DOT + documentFormat)), 1, 0);
		gridPane.add(createImageView(vboxElement), 2, 0);

		if (vboxElement.getChildren().isEmpty()) {
			vboxElement.getChildren().add(gridPane);
		}

		((ImageView) ((VBox) (((HBox) vboxElement.getParent()).getChildren().get(0))).getChildren().get(1))
				.setImage(new Image(this.getClass().getResourceAsStream(RegistrationConstants.DONE_IMAGE_PATH), 15, 15,
						true, true));

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Scan document added to Vbox element");

	}

	/**
	 * This method will display the scanned document
	 */
	private void displayDocument(byte[] document, String documentName) {

		/*
		 * TODO - pdf to images to be replaced wit ketrnal and setscanner factory has to
		 * be removed here
		 */
		if (RegistrationConstants.YES
				.equalsIgnoreCase(getValueFromApplicationContext(RegistrationConstants.DOC_SCANNER_ENABLED))) {
			documentScanFacade.setScannerFactory();
		} else {
			documentScanFacade.setStubScannerFactory();
		}
		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Converting bytes to Image to display scanned document");
		/* clearing the previously loaded pdf pages inorder to clear up the memory */
		initializePreviewSection();
		if (RegistrationConstants.PDF
				.equalsIgnoreCase(documentName.substring(documentName.lastIndexOf(RegistrationConstants.DOT) + 1))) {
			try {
				docPages = documentScanFacade.pdfToImages(document);
				if (!docPages.isEmpty()) {

					if (docPages.size() > 1) {
						docPageNumber.setText(RegistrationConstants.ONE);
						docPreviewNext.setVisible(true);
						docPreviewPrev.setVisible(true);
						docPreviewNext.setDisable(false);
					}

					//docPreviewImgGroup.getChildren().clear();
					//docPreviewImgGroup.getChildren().add(new ImageView(getImage(docPages.get(0))));
					docPreviewImgView.preserveRatioProperty().set(true);
					docPreviewImgView.setImage(getImage(docPages.get(0)));

					docPreviewLabel.setVisible(true);

				}
			} catch (IOException ioException) {
				LOGGER.error("DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						ioException.getMessage() + ExceptionUtils.getStackTrace(ioException));
				generateAlert(RegistrationConstants.ERROR, RegistrationUIConstants.PREVIEW_DOC);
				return;
			}
		} else {
			docPreviewLabel.setVisible(true);
			//docPreviewImgGroup.getChildren().clear();
			//docPreviewImgGroup.getChildren().add(new ImageView(new Image(new ByteArrayInputStream(document))));
			docPreviewImgView.setImage(convertBytesToImage(document));
		}
		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Scanned document displayed succesfully");
	}

	/**
	 * This method will preview the next document
	 */
	public void previewNextPage() {

		if (isDocsNotEmpty()) {
			int pageNumber = Integer.parseInt(docPageNumber.getText());
			if (docPages.size() > pageNumber) {
				setDocPreview(pageNumber, pageNumber + 1);
				docPreviewPrev.setDisable(false);
				if ((pageNumber + 1) == docPages.size()) {
					docPreviewNext.setDisable(true);
				}
			}
		}
	}

	/**
	 * This method will preview the previous document
	 */
	public void previewPrevPage() {
		if (isDocsNotEmpty()) {
			int pageNumber = Integer.parseInt(docPageNumber.getText());
			if (pageNumber > 1) {
				setDocPreview(pageNumber - 2, pageNumber - 1);
				docPreviewNext.setDisable(false);
				if ((pageNumber - 1) == 1) {
					docPreviewPrev.setDisable(true);
				}
			}
		}
	}

	/**
	 * This method will determine if the document is empty
	 */
	private boolean isDocsNotEmpty() {
		return StringUtils.isNotEmpty(docPageNumber.getText()) && docPages != null && !docPages.isEmpty();
	}

	/**
	 * This method will set the inde and page number for the document
	 *
	 * @param index      - index of the preview section
	 * @param pageNumber - page number for the preview section
	 */
	private void setDocPreview(int index, int pageNumber) {
		docPreviewImgView.setImage(SwingFXUtils.toFXImage(docPages.get(index), null));
		//docPreviewImgGroup.getChildren().clear();
		//docPreviewImgGroup.getChildren().add(new ImageView(getImage(docPages.get(index))));
		docPageNumber.setText(String.valueOf(pageNumber));
	}

	/**
	 * This method will create Image to delete scanned document
	 *
	 * @param vboxElement the {@link VBox}
	 */
	private ImageView createImageView(VBox vboxElement) {

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID,
				"Binding OnAction event for Image to delete the attached document");

		Image image = new Image(this.getClass().getResourceAsStream(RegistrationConstants.CLOSE_IMAGE_PATH), 15, 15,
				true, true);
		ImageView imageView = new ImageView(image);
		imageView.setCursor(Cursor.HAND);

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Creating Image to delete the attached document");

		imageView.setOnMouseClicked((event) -> {

			String hyperLinkArray[] = ((ImageView) event.getSource()).getParent().getId().split("_");

			LOGGER.debug("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID,
					" Document On Mouse click listener started for delete " + hyperLinkArray[0]);

			AuditEvent auditEvent = null;
			try {
				auditEvent = AuditEvent.valueOf(String.format("REG_DOC_%S_DELETE", hyperLinkArray[0]));
			} catch (Exception exception) {

				LOGGER.error("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID,
						"Unable to find audit event for button : " + hyperLinkArray[0]);

				auditEvent = AuditEvent.REG_DOC_DELETE;

			}

			auditFactory.audit(auditEvent, Components.REG_DOCUMENTS, SessionContext.userId(),
					AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			HBox hbox = (HBox) vboxElement.getParent();
			ComboBox<String> comboBox = (ComboBox) hbox.getChildren().get(1);

			/** label not disabled as not clearing combo box */
//			(((VBox) hbox.getParent()).lookup(RegistrationConstants.HASH + hbox.getId() + RegistrationConstants.LABEL))
//					.setVisible(false);

			((ImageView) ((VBox) ((hbox).getChildren().get(0))).getChildren().get(1)).setImage(new Image(
					this.getClass().getResourceAsStream(RegistrationConstants.CLOSE_IMAGE_PATH), 15, 15, true, true));

			initializePreviewSection();

			GridPane gridpane = (GridPane) ((ImageView) event.getSource()).getParent();
			String key = comboBox.getId();
			getDocumentsMapFromSession().remove(key);
			comboBox.getSelectionModel().clearSelection();
			ObservableList<Node> nodes = ((HBox) vboxElement.getParent()).getChildren();
			for (Node node : nodes) {
				if (node instanceof VBox) {

					for (Node vBoxChilder : ((VBox) node).getChildren()) {

						if (vBoxChilder instanceof GridPane) {

							if (((GridPane) vBoxChilder).getChildren() != null) {

								for (Node gridPaneChid : ((GridPane) vBoxChilder).getChildren()) {

									if (gridPaneChid instanceof TextField) {

										((TextField) gridPaneChid).setText("");
//										((TextField) gridPaneChid).setPromptText(RegistrationUIConstants.REF_NUMBER);
									}
								}
							}
						}
					}

				}
			}

			vboxElement.getChildren().remove(gridpane);

			validateDocumentsPane();

			LOGGER.debug("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID,
					" Document On Mouse click listener started for delete " + hyperLinkArray[0]);

		});

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Image added to delete the attached document");

		return imageView;
	}

	/**
	 * This method will create Hyperlink to view scanned document
	 *
	 * @param document the {@link String}
	 */
	private Hyperlink createHyperLink(String document) {

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Creating Hyperlink to display Scanned document");

		Hyperlink hyperLink = new Hyperlink();
		hyperLink.setId(document);
		// hyperLink.setGraphic(new ImageView(new
		// Image(this.getClass().getResourceAsStream(RegistrationConstants.VIEW))));
		hyperLink.getStyleClass().add(RegistrationConstants.DOCUMENT_VIEW_ICON);
		hyperLink.setTooltip(new Tooltip(RegistrationConstants.EYETOOLTIP));
		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID,
				"Binding OnAction event to Hyperlink to display Scanned document");
		hyperLink.setOnAction((actionEvent) -> {

			LOGGER.debug("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Document action listener started for view ");

			actionEvent.getEventType().getName();
			GridPane pane = (GridPane) ((Hyperlink) actionEvent.getSource()).getParent();
			String hyperLinkArray[] = ((Hyperlink) actionEvent.getSource()).getId().split("_");
			String documentKey = ((VBox) pane.getParent()).getId();
			cropDocumentKey = documentKey;

			AuditEvent auditEvent = null;
			try {
				auditEvent = AuditEvent.valueOf(String.format("REG_DOC_%S_VIEW", hyperLinkArray[0]));
			} catch (Exception exception) {

				LOGGER.error("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
						RegistrationConstants.APPLICATION_ID,
						"Unable to find audit event for button : " + hyperLinkArray[0]);

				auditEvent = AuditEvent.REG_DOC_VIEW;

			}

			auditFactory.audit(auditEvent, Components.REG_DOCUMENTS, SessionContext.userId(),
					AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

			DocumentDto selectedDocumentToDisplay = getDocumentsMapFromSession().get(documentKey);

			if (selectedDocumentToDisplay != null) {
				displayDocument(selectedDocumentToDisplay.getDocument(), selectedDocumentToDisplay.getValue()
						+ RegistrationConstants.DOT + selectedDocumentToDisplay.getFormat());
			}

			LOGGER.debug("REGISTRATION - DOCUMENT_SCAN_CONTROLLER", APPLICATION_NAME,
					RegistrationConstants.APPLICATION_ID, "Document action listener completed for view ");

		});

		LOGGER.info(RegistrationConstants.DOCUMNET_SCAN_CONTROLLER, RegistrationConstants.APPLICATION_NAME,
				RegistrationConstants.APPLICATION_ID, "Hyperlink added to display Scanned document");

		return hyperLink;
	}

	/**
	 * This method will prepare the edit page content
	 */
	protected void prepareEditPageContent() {

		FXUtils fxUtils = FXUtils.getInstance();

		if (documentComboBoxes != null && !documentComboBoxes.isEmpty()) {
			Map<String, DocumentDto> documentsMap = getDocumentsMapFromSession();
			for (String docCategoryKey : documentsMap.keySet()) {
				addDocumentsToScreen(documentsMap.get(docCategoryKey).getValue(),
						documentsMap.get(docCategoryKey).getFormat(), documentVBoxes.get(docCategoryKey));
				fxUtils.selectComboBoxValue(documentComboBoxes.get(docCategoryKey),
						documentsMap.get(docCategoryKey).getValue());
			}

		}

	}

	/**
	 * This method will clear the document section
	 */
	public void clearDocSection() {
		clearAllDocs();
		initializePreviewSection();
	}

	/**
	 * This method will clear for all the documents
	 */
	private void clearAllDocs() {

		for (String docCategoryKey : documentVBoxes.keySet()) {

			documentVBoxes.get(docCategoryKey).getChildren().clear();
		}

	}

	/**
	 * This method will intialize the preview section
	 */
	public void initializePreviewSection() {

		docPreviewLabel.setVisible(false);
		docPreviewNext.setVisible(false);
		docPreviewPrev.setVisible(false);

		docPreviewNext.setDisable(true);
		docPreviewPrev.setDisable(true);
		docPageNumber.setText(RegistrationConstants.EMPTY);
		//docPreviewImgGroup.getChildren().clear();
		docPreviewImgView.setImage(null);
		docPages = null;
	}

	/**
	 * This method is to go to previous page
	 */
	@FXML
	private void back() {
		auditFactory.audit(AuditEvent.REG_DOC_BACK, Components.REG_DOCUMENTS, SessionContext.userId(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		registrationController.showCurrentPage(RegistrationConstants.DOCUMENT_SCAN,
				getPageByAction(RegistrationConstants.DOCUMENT_SCAN, RegistrationConstants.PREVIOUS));
	}

	/**
	 * This method is to go to next page
	 */
	@FXML
	private void next() {
		auditFactory.audit(AuditEvent.REG_DOC_NEXT, Components.REG_DOCUMENTS, SessionContext.userId(),
				AuditReferenceIdTypes.USER_ID.getReferenceTypeId());

		registrationController.showCurrentPage(RegistrationConstants.DOCUMENT_SCAN,
				getPageByAction(RegistrationConstants.DOCUMENT_SCAN, RegistrationConstants.NEXT));

		guardianBiometricsController.populateBiometricPage(false, false);
		/*
		 * biometricExceptionController.disableNextBtn();
		 * fingerPrintCaptureController.clearImage();
		 * irisCaptureController.clearIrisBasedOnExceptions();
		 * //guardianBiometricsController.manageBiometricsListBasedOnExceptions();
		 *
		 * if (getRegistrationDTOFromSession().getSelectionListDTO() != null) { //TODO
		 * document pane validation Anusha if (true) {
		 * SessionContext.map().put(RegistrationConstants.UIN_UPDATE_DOCUMENTSCAN,
		 * false); updateUINMethodFlow(); demographicDetailController.saveDetail();
		 * registrationController.showCurrentPage(RegistrationConstants.DOCUMENT_SCAN,
		 * getPageByAction(RegistrationConstants.DOCUMENT_SCAN,
		 * RegistrationConstants.NEXT));
		 * //registrationController.showUINUpdateCurrentPage(); } } else { if
		 * (RegistrationConstants.ENABLE
		 * .equalsIgnoreCase(getValueFromApplicationContext(RegistrationConstants.
		 * DOC_DISABLE_FLAG))) { if (true) {
		 * registrationController.showCurrentPage(RegistrationConstants.DOCUMENT_SCAN,
		 * getPageByAction(RegistrationConstants.DOCUMENT_SCAN,
		 * RegistrationConstants.NEXT)); } } else {
		 * registrationController.showCurrentPage(RegistrationConstants.DOCUMENT_SCAN,
		 * getPageByAction(RegistrationConstants.DOCUMENT_SCAN,
		 * RegistrationConstants.NEXT));
		 *
		 * } }
		 */

	}

	// TODO This need to be validated using MVEL Expression - @Anusha
	private void validateDocumentsPane() {

		if (RegistrationConstants.DISABLE.equalsIgnoreCase(
				String.valueOf(ApplicationContext.map().get(RegistrationConstants.DOC_DISABLE_FLAG)))) {
			continueBtn.setDisable(false);
		} else {
			if (registrationController.validateDemographicPane(documentScanPane)) {
				continueBtn.setDisable(false);
			} else {
				continueBtn.setDisable(true);
			}
		}
	}

	public List<BufferedImage> getScannedPages() {
		return scannedPages;
	}

	public void setScannedPages(List<BufferedImage> scannedPages) {
		this.scannedPages = scannedPages;
	}

	/**
	 * Sets the value of the biometric exception required based on the individual
	 * whose biometric exceptions has to be captured. If exception of Parent or
	 * Guardian is required, text will be displayed as Parent Or guardian biometrics
	 * exception required. While for Individual, text will be displayed as Biometric
	 * exception required.
	 *
	 * @param isParentOrGuardianBiometricsCaptured boolean value indicating whose
	 *                                             biometric exception has to be
	 *                                             captured either individual or
	 *                                             parent/ guardian
	 */
	public void setExceptionDescriptionText(boolean isParentOrGuardianBiometricsCaptured) {
		ResourceBundle applicationLanguage = ApplicationContext.applicationLanguageBundle();

		String exceptionFaceDescription = applicationLanguage.getString("biometricexceptionrequired");

		if (isParentOrGuardianBiometricsCaptured) {
			exceptionFaceDescription = applicationLanguage.getString("parentOrGuardian").concat(" ")
					.concat(exceptionFaceDescription.toLowerCase());
		}

		biometricExceptionReq.setText(exceptionFaceDescription);
	}

	private List<UiSchemaDTO> getDocId() {

		return validation.getValidationMap().entrySet().stream()
				.filter(map -> map.getValue().getType().equalsIgnoreCase("documentType")).map(m -> m.getValue())
				.collect(Collectors.toList());

	}

	public BufferedImage getScannedImage(int docPageNumber) {

		return scannedPages.get(docPageNumber);
	}

}
