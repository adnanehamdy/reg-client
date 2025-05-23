package io.mosip.registration.controller.reg;

import static io.mosip.registration.constants.RegistrationConstants.APPLICATION_NAME;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.config.AppConfig;
import io.mosip.registration.constants.LoggerConstants;
import io.mosip.registration.constants.RegistrationConstants;
import io.mosip.registration.constants.RegistrationUIConstants;
import io.mosip.registration.context.ApplicationContext;
import io.mosip.registration.controller.BaseController;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;

/**
 * Class for validating the date fields
 * 
 * @author Taleev.Aalam
 * @author Balaji
 * @since 1.0.0
 *
 */
@Component
public class DateValidation extends BaseController {

	private static final Logger LOGGER = AppConfig.getLogger(DateValidation.class);
	@Autowired
	private Validations validation;

	int maxAge = 0;

	public boolean isNewValueValid(String newValue, String fieldType) {
		if(newValue.isEmpty())
			return true;

		if(newValue.matches(RegistrationConstants.NUMBER_REGEX)) {
			switch (fieldType) {
				case RegistrationConstants.DD:
					return Integer.parseInt(newValue) > RegistrationConstants.DAYS ? false : true;
				case RegistrationConstants.MM:
					return Integer.parseInt(newValue) > RegistrationConstants.MONTH ? false : true;
				case RegistrationConstants.YYYY:
					return newValue.length() > 4 ? false : true;
				case RegistrationConstants.AGE_FIELD:
					return Integer.parseInt(newValue) < 0 ? false : true;
			}
		}
		return false;
	}

	public boolean validateDate(Pane parentPane, String fieldId) {
		resetFieldStyleClass(parentPane, fieldId, false, getValueFromApplicationContext(RegistrationConstants.MAX_AGE));

		TextField dd = (TextField) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.DD);
		TextField mm = (TextField) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.MM);
		TextField yyyy = (TextField) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.YYYY);

		boolean isValid = false;
		if(dd.getText().matches(RegistrationConstants.NUMBER_REGEX) &&
				mm.getText().matches(RegistrationConstants.NUMBER_REGEX) &&
				yyyy.getText().matches(RegistrationConstants.NUMBER_REGEX) &&
				yyyy.getText().matches(RegistrationConstants.FOUR_NUMBER_REGEX)) {

			isValid = isValidDate(parentPane, dd.getText(), mm.getText(), yyyy.getText(), fieldId);
			if(isValid) {
				setLocalDateFields(parentPane, dd, mm, yyyy);
				populateAge(parentPane, fieldId);
			}
		}

		resetFieldStyleClass(parentPane, fieldId, !isValid, getValueFromApplicationContext(RegistrationConstants.MAX_AGE));
		return isValid;
	}
	
	public boolean validateDateWithMaxAndMinDays(Pane parentPane, String fieldId, int minDate, int maxDate) {
		resetFieldStyleClass(parentPane, fieldId, false, minDate+"-"+maxDate);
		
		TextField dd = (TextField) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.DD);
		TextField mm = (TextField) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.MM);
		TextField yyyy = (TextField) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.YYYY);

		boolean isValid = false;
		if(dd.getText().matches(RegistrationConstants.NUMBER_REGEX) &&
				mm.getText().matches(RegistrationConstants.NUMBER_REGEX) &&
				yyyy.getText().matches(RegistrationConstants.NUMBER_REGEX) &&
				yyyy.getText().matches(RegistrationConstants.FOUR_NUMBER_REGEX)) {
			isValid = isValidDateContent(parentPane, dd.getText(), mm.getText(), yyyy.getText(), fieldId, minDate, maxDate);
			if (isValid) {
				setLocalDateFields(parentPane, dd, mm, yyyy);
				populateAge(parentPane, fieldId);
			}
		}

		resetFieldStyleClass(parentPane, fieldId, !isValid, minDate+"-"+maxDate);
		return isValid;
	}

	public boolean validateAge(Pane parentPane, TextField ageField) {
		String fieldId = ageField.getId().split("__")[0];
		resetFieldStyleClass(parentPane, fieldId, false, getValueFromApplicationContext(RegistrationConstants.MAX_AGE));
		boolean isValid = ageField.getText().matches(RegistrationConstants.NUMBER_REGEX);

		if(isValid) {
			int maxAge = Integer.parseInt(getValueFromApplicationContext(RegistrationConstants.MAX_AGE));
			try {
				int age = Integer.parseInt(ageField.getText());
				if(age > maxAge)
					isValid = false;
				else {

					Calendar defaultDate = Calendar.getInstance(TimeZone.getTimeZone(ZoneId.of("UTC")));
					defaultDate.set(Calendar.DATE, 1);
					defaultDate.set(Calendar.MONTH, 0);
					defaultDate.add(Calendar.YEAR, -age);

					LocalDate date = LocalDate.of(defaultDate.get(Calendar.YEAR), defaultDate.get(Calendar.MONTH) + 1,
							defaultDate.get(Calendar.DATE));
					isValid = validation.validateSingleString(date.format(DateTimeFormatter.ofPattern(ApplicationContext.getDateFormat())),
							fieldId);

					if(isValid) {
						Node node = getFxElement(parentPane, ageField.getId()+RegistrationConstants.LOCAL_LANGUAGE);
						if(node != null) {  ((TextField)node).setText(String.valueOf(age)); }
						populateDateFields(parentPane, fieldId, age);
					}
				}
			} catch (Exception ex) {
				LOGGER.error(LoggerConstants.DATE_VALIDATION, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID,
						ExceptionUtils.getStackTrace(ex));
				isValid = false;
			}
		}

		resetFieldStyleClass(parentPane, fieldId, !isValid, getValueFromApplicationContext(RegistrationConstants.MAX_AGE));
		return isValid;
	}

	private void resetFieldStyleClass(Pane parentPane, String fieldId, boolean isError, String allowedLimit) {
		TextField dd = (TextField)getFxElement(parentPane, fieldId + "__" + RegistrationConstants.DD);
		TextField mm = (TextField)getFxElement(parentPane, fieldId + "__" + RegistrationConstants.MM);
		TextField yyyy = (TextField)getFxElement(parentPane, fieldId + "__" + RegistrationConstants.YYYY);
		TextField ageField = (TextField)getFxElement(parentPane, fieldId + "__" + RegistrationConstants.AGE_FIELD);
		TextField expiryDateField = (TextField)getFxElement(parentPane, fieldId + "_" + RegistrationConstants.DATE);

		Label dobMessage = (Label) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.DOB_MESSAGE);

		setTextFieldStyle(parentPane, dd, isError);
		setTextFieldStyle(parentPane, mm, isError);
		setTextFieldStyle(parentPane, yyyy, isError);
		setTextFieldStyle(parentPane, ageField, isError);
		setTextFieldStyle(parentPane, ageField, isError);
		setTextFieldStyle(parentPane, expiryDateField, isError);
		if(ageField != null) {
			if(isError) {
				dobMessage.setText(RegistrationUIConstants.INVALID_DATE.concat(" / ")
						.concat(RegistrationUIConstants.INVALID_AGE).concat(allowedLimit));
				dobMessage.setVisible(true);
				generateAlert(parentPane, RegistrationConstants.DOB, dobMessage.getText());
			} else {
				dobMessage.setText(RegistrationConstants.EMPTY);
				dobMessage.setVisible(false);
			}
		}else {
			if(isError) {
				dobMessage.setText(RegistrationUIConstants.INVALID_DATE.concat(" / ")
						.concat(RegistrationUIConstants.INVALID_DATE_LIMIT).concat(allowedLimit));
				dobMessage.setVisible(true);
				generateAlert(parentPane, RegistrationConstants.DATE, dobMessage.getText());
			} else {
				dobMessage.setText(RegistrationConstants.EMPTY);
				dobMessage.setVisible(false);
			}
		}
	}

	private void populateAge(Pane parentPane, String fieldId) {
		LocalDate date = getCurrentSetDate(parentPane, fieldId);
		TextField ageField = (TextField)getFxElement(parentPane, fieldId + "__" + RegistrationConstants.AGE_FIELD);
		if (ageField != null) {
			String age = String.valueOf(Period.between(date, LocalDate.now(ZoneId.of("UTC"))).getYears());
			if(!age.equals(ageField.getText())) { ageField.setText(age); }
			Node node = getFxElement(parentPane, ageField.getId()+RegistrationConstants.LOCAL_LANGUAGE);
			if(node != null) {
				TextField localTextField = (TextField)node;
				if(!age.equals(localTextField.getText())) { localTextField.setText(age); }
			}
		}		
	}

	private void populateDateFields(Pane parentPane, String fieldId, int age) {
		TextField dd = (TextField)getFxElement(parentPane, fieldId + "__" + RegistrationConstants.DD);
		TextField mm = (TextField)getFxElement(parentPane, fieldId + "__" + RegistrationConstants.MM);
		TextField yyyy = (TextField)getFxElement(parentPane, fieldId + "__" + RegistrationConstants.YYYY);

		try {
			LocalDate date = LocalDate.of(Integer.valueOf(yyyy.getText()), Integer.valueOf(mm.getText()), Integer.valueOf(dd.getText()));
			if(Period.between(date,LocalDate.now(ZoneId.of("UTC"))).getYears() == age) {
				setLocalDateFields(parentPane, dd, mm, yyyy);
				return;
			}
		} catch (Throwable t) {
			LOGGER.error(LoggerConstants.DATE_VALIDATION, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID, t.getMessage());
		}

		Calendar defaultDate = Calendar.getInstance(TimeZone.getTimeZone(ZoneId.of("UTC")));
		defaultDate.set(Calendar.DATE, 1);
		defaultDate.set(Calendar.MONTH, 0);
		defaultDate.add(Calendar.YEAR, -age);

		dd.setText(String.valueOf(defaultDate.get(Calendar.DATE)));
		mm.setText(String.valueOf(defaultDate.get(Calendar.MONTH) + 1));
		yyyy.setText(String.valueOf(defaultDate.get(Calendar.YEAR)));

		setLocalDateFields(parentPane, dd, mm, yyyy);
	}

	private boolean isValidDate(Pane parentPane, String dd, String mm, String yyyy, String fieldId) {
		if(isValidValue(dd) && isValidValue(mm) && isValidValue(yyyy)) {
			try {
				LocalDate date = LocalDate.of(Integer.valueOf(yyyy), Integer.valueOf(mm), Integer.valueOf(dd));
				String dob = date.format(DateTimeFormatter.ofPattern(ApplicationContext.getDateFormat()));
				return validation.validateSingleString(dob, fieldId);
			} catch (Exception ex) {
				LOGGER.error(LoggerConstants.DATE_VALIDATION, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID, ExceptionUtils.getStackTrace(ex));
			}
		}
		return false;
	}
	
	private boolean isValidDateContent(Pane parentPane, String dd, String mm, String yyyy, String fieldId, int minDate, int maxDate) {
		if(isValidValue(dd) && isValidValue(mm) && isValidValue(yyyy)) {
			try {
				LocalDate date = LocalDate.of(Integer.valueOf(yyyy), Integer.valueOf(mm), Integer.valueOf(dd));
				String dateContent = date.format(DateTimeFormatter.ofPattern(ApplicationContext.getDateFormat()));
				return validation.validateDateWithMaxAndMinDays(dateContent, fieldId, minDate, maxDate);
			} catch (Exception ex) {
				LOGGER.error(LoggerConstants.DATE_VALIDATION, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID, ExceptionUtils.getStackTrace(ex));
			}
		}
		return false;
	}

	private boolean isValidValue(String value) {
		return value != null && !value.isEmpty();
	}

	private LocalDate getCurrentSetDate(Pane parentPane, String fieldId) {
		TextField dd = (TextField) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.DD);
		TextField mm = (TextField) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.MM);
		TextField yyyy = (TextField) getFxElement(parentPane, fieldId+ "__" + RegistrationConstants.YYYY);

		if(isValidValue(dd.getText()) && isValidValue(mm.getText()) && isValidValue(yyyy.getText())) {
			try {
				return LocalDate.of(Integer.valueOf(yyyy.getText()), Integer.valueOf(mm.getText()), Integer.valueOf(dd.getText()));
			} catch (Throwable ex) {
				LOGGER.error(LoggerConstants.DATE_VALIDATION, APPLICATION_NAME, RegistrationConstants.APPLICATION_ID, ExceptionUtils.getStackTrace(ex));
			}
		}
		return null;
	}

	private void setLocalDateFields(Pane parentPane, TextField dd, TextField mm, TextField yyyy) {
		Node local_dd = getFxElement(parentPane, dd.getId() + RegistrationConstants.LOCAL_LANGUAGE);
		Node local_mm = getFxElement(parentPane, mm.getId() + RegistrationConstants.LOCAL_LANGUAGE);
		Node local_yyyy = getFxElement(parentPane, yyyy.getId() + RegistrationConstants.LOCAL_LANGUAGE);
		if(local_dd != null)
			((TextField)local_dd).setText(dd.getText());
		if(local_mm != null)
			((TextField)local_mm).setText(mm.getText());
		if(local_yyyy != null)
			((TextField)local_yyyy).setText(yyyy.getText());
	}


	private void setTextFieldStyle(Pane parentPane, TextField node, boolean isError) {
		if(parentPane == null || node == null)  { return; }
		Node labelNode = getFxElement(parentPane, node.getId() + RegistrationConstants.LABEL);
		Node headerNode = getFxElement(parentPane, node.getId().split("__")[0] + RegistrationConstants.LABEL);
		if(labelNode == null) { return; }
		Label label = (Label)labelNode;
		Label headerLabel = (Label)headerNode;
		if(isError) {
			node.getStyleClass().clear();
			node.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD_FOCUSED);
			label.getStyleClass().clear();
			label.getStyleClass().add("demoGraphicFieldLabelOnType");
			headerLabel.getStyleClass().clear();
			headerLabel.getStyleClass().add("demoGraphicFieldLabelOnType");
		}
		else {
			node.getStyleClass().clear();
			node.getStyleClass().add(RegistrationConstants.DEMOGRAPHIC_TEXTFIELD);
		}
	}

	private Node getFxElement(Pane pane, String fieldId) {
		Node node = pane.lookup(RegistrationConstants.HASH + fieldId);
		if(node == null)
			node = pane.getParent().getParent().getParent().lookup(RegistrationConstants.HASH + fieldId);
		return node;
	}
}
