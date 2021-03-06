/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.components.paymentmethods;

import io.bitsquare.common.util.Tuple2;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.BICValidator;
import io.bitsquare.gui.util.validation.IBANValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.*;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.payment.SepaAccount;
import io.bitsquare.payment.SepaAccountContractData;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.bitsquare.gui.util.FormBuilder.*;

public class SepaForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(SepaForm.class);

    private final SepaAccount sepaAccount;
    private final IBANValidator ibanValidator;
    private final BICValidator bicValidator;
    private InputTextField ibanInputTextField;
    private InputTextField bicInputTextField;
    private TextField currencyTextField;
    private final List<CheckBox> euroCountryCheckBoxes = new ArrayList<>();
    private final List<CheckBox> nonEuroCountryCheckBoxes = new ArrayList<>();

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(paymentAccountContractData.getPaymentMethodName()));
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account holder name:", ((SepaAccountContractData) paymentAccountContractData).getHolderName());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "IBAN:", ((SepaAccountContractData) paymentAccountContractData).getIban());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "BIC/SWIFT:", ((SepaAccountContractData) paymentAccountContractData).getBic());
        addAllowedPeriod(gridPane, ++gridRow, paymentAccountContractData);
        return gridRow;
    }

    public SepaForm(PaymentAccount paymentAccount, IBANValidator ibanValidator, BICValidator bicValidator, InputValidator inputValidator,
                    GridPane gridPane, int gridRow) {
        super(paymentAccount, inputValidator, gridPane, gridRow);
        this.sepaAccount = (SepaAccount) paymentAccount;
        this.ibanValidator = ibanValidator;
        this.bicValidator = bicValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account holder name:").second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setHolderName(newValue);
            updateFromInputs();
        });

        ibanInputTextField = addLabelInputTextField(gridPane, ++gridRow, "IBAN:").second;
        ibanInputTextField.setValidator(ibanValidator);
        ibanInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setIban(newValue);
            updateFromInputs();

        });
        bicInputTextField = addLabelInputTextField(gridPane, ++gridRow, "BIC/SWIFT:").second;
        bicInputTextField.setValidator(bicValidator);
        bicInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setBic(newValue);
            updateFromInputs();

        });

        Tuple2<Label, ComboBox> tuple2 = addLabelComboBox(gridPane, ++gridRow, "Country of your Bank:");
        ComboBox<Country> countryComboBox = tuple2.second;
        countryComboBox.setPromptText("Select country of your Bank");
        countryComboBox.setConverter(new StringConverter<Country>() {
            @Override
            public String toString(Country country) {
                return country.code + " (" + country.name + ")";
            }

            @Override
            public Country fromString(String s) {
                return null;
            }
        });
        countryComboBox.setOnAction(e -> {
            Country selectedItem = countryComboBox.getSelectionModel().getSelectedItem();
            sepaAccount.setCountry(selectedItem);
            TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(selectedItem.code);
            sepaAccount.setSingleTradeCurrency(currency);
            currencyTextField.setText(currency.getCodeAndName());
            updateCountriesSelection(true, euroCountryCheckBoxes);
            updateCountriesSelection(true, nonEuroCountryCheckBoxes);
            updateFromInputs();
        });

        currencyTextField = addLabelTextField(gridPane, ++gridRow, "Currency:").second;

        addEuroCountriesGrid(true);
        addNonEuroCountriesGrid(true);
        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();

        countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllSepaCountries()));
        Country country = CountryUtil.getDefaultCountry();
        countryComboBox.getSelectionModel().select(country);
        sepaAccount.setCountry(country);
        TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(country.code);
        sepaAccount.setSingleTradeCurrency(currency);
        currencyTextField.setText(currency.getCodeAndName());
        updateFromInputs();
    }

    private void addEuroCountriesGrid(boolean isEditable) {
        addCountriesGrid(isEditable, "Accept trades from those Euro countries:", euroCountryCheckBoxes, CountryUtil.getAllSepaEuroCountries());
    }

    private void addNonEuroCountriesGrid(boolean isEditable) {
        addCountriesGrid(isEditable, "Accept trades from those non-Euro countries:", nonEuroCountryCheckBoxes, CountryUtil.getAllSepaNonEuroCountries());
    }

    private void addCountriesGrid(boolean isEditable, String title, List<CheckBox> checkBoxList, List<Country> dataProvider) {
        Label label = addLabel(gridPane, ++gridRow, title, 0);
        label.setWrapText(true);
        label.setPrefWidth(200);
        label.setTextAlignment(TextAlignment.RIGHT);
        GridPane.setValignment(label, VPos.TOP);
        FlowPane flowPane = new FlowPane();
        flowPane.setPadding(new Insets(10, 10, 10, 10));
        flowPane.setVgap(10);
        flowPane.setHgap(10);

        if (isEditable)
            flowPane.setId("flowpane-checkboxes-bg");
        else
            flowPane.setId("flowpane-checkboxes-non-editable-bg");

        dataProvider.stream().forEach(country ->
        {
            final String countryCode = country.code;
            CheckBox checkBox = new CheckBox(countryCode);
            checkBox.setUserData(countryCode);
            checkBoxList.add(checkBox);
            checkBox.setMouseTransparent(!isEditable);
            checkBox.setMinWidth(45);
            checkBox.setTooltip(new Tooltip(country.name));
            checkBox.setOnAction(event -> {
                if (checkBox.isSelected())
                    sepaAccount.addAcceptedCountry(countryCode);
                else
                    sepaAccount.removeAcceptedCountry(countryCode);

                updateAllInputsValid();
            });
            flowPane.getChildren().add(checkBox);
        });
        updateCountriesSelection(isEditable, checkBoxList);

        GridPane.setRowIndex(flowPane, gridRow);
        GridPane.setColumnIndex(flowPane, 1);
        gridPane.getChildren().add(flowPane);
    }

    private void updateCountriesSelection(boolean isEditable, List<CheckBox> checkBoxList) {
        checkBoxList.stream().forEach(checkBox -> {
            String countryCode = (String) checkBox.getUserData();
            TradeCurrency selectedCurrency = sepaAccount.getSelectedTradeCurrency();
            if (selectedCurrency == null)
                selectedCurrency = CurrencyUtil.getCurrencyByCountryCode(CountryUtil.getDefaultCountry().code);

            boolean selected;

            if (isEditable) {
                selected = CurrencyUtil.getCurrencyByCountryCode(countryCode).getCode().equals(selectedCurrency.getCode());

                if (selected)
                    sepaAccount.addAcceptedCountry(countryCode);
                else
                    sepaAccount.removeAcceptedCountry(countryCode);
            } else {
                selected = sepaAccount.getAcceptedCountryCodes().contains(countryCode);
            }
            checkBox.setSelected(selected);
        });
    }

    @Override
    protected void autoFillNameTextField() {
        if (autoFillCheckBox != null && autoFillCheckBox.isSelected()) {
            String iban = ibanInputTextField.getText();
            if (iban.length() > 5)
                iban = "..." + iban.substring(iban.length() - 5, iban.length());
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            String country = paymentAccount.getCountry() != null ? paymentAccount.getCountry().code : "?";
            String currency = paymentAccount.getSingleTradeCurrency() != null ? paymentAccount.getSingleTradeCurrency().getCode() : "?";
            accountNameTextField.setText(method.concat(", ").concat(currency).concat(", ").concat(country).concat(", ").concat(iban));
        }
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && bicValidator.validate(sepaAccount.getBic()).isValid
                && ibanValidator.validate(sepaAccount.getIban()).isValid
                && inputValidator.validate(sepaAccount.getHolderName()).isValid
                && sepaAccount.getAcceptedCountryCodes().size() > 0
                && sepaAccount.getSingleTradeCurrency() != null
                && sepaAccount.getCountry() != null);
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, "Account name:", sepaAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(sepaAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", sepaAccount.getHolderName());
        addLabelTextField(gridPane, ++gridRow, "IBAN:", sepaAccount.getIban());
        addLabelTextField(gridPane, ++gridRow, "BIC/SWIFT:", sepaAccount.getBic());
        addLabelTextField(gridPane, ++gridRow, "Location of Bank:", sepaAccount.getCountry().name);
        addLabelTextField(gridPane, ++gridRow, "Currency:", sepaAccount.getSingleTradeCurrency().getCodeAndName());
        addAllowedPeriod();
        addEuroCountriesGrid(false);
        addNonEuroCountriesGrid(false);
    }
}
