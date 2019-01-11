package com.gettipsi.stripe.dialog;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;
import android.content.Context;
import android.text.TextUtils;

import com.devmarvel.creditcardentry.fields.SecurityCodeText;
import com.devmarvel.creditcardentry.library.CreditCard;
import com.devmarvel.creditcardentry.library.CreditCardForm;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.gettipsi.stripe.R;
import com.gettipsi.stripe.StripeModule;
import com.gettipsi.stripe.util.CardFlipAnimator;
import com.gettipsi.stripe.util.Utils;
import com.stripe.android.Stripe;
import com.stripe.android.TokenCallback;
import com.stripe.android.model.Card;
import com.stripe.android.model.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;


/**
 * Created by dmitriy on 11/13/16
 */

public class AddCardDialogFragment extends DialogFragment {

    private static final String KEY = "KEY";
    private static final String ADDRESS_KEY = "address";
    private static final String NAME_KEY = "cardholder";
    private static final String TAG = AddCardDialogFragment.class.getSimpleName();
    private static final String CCV_INPUT_CLASS_NAME = SecurityCodeText.class.getSimpleName();
    private String PUBLISHABLE_KEY;
    private String SHOW_ADDRESS;
    private String CARDHOLDER_NAME;

    private ProgressBar progressBar;
    private CreditCardForm from;
    private ImageView imageFlipedCard;
    private ImageView imageFlipedCardBack;

    private volatile Promise promise;
    private boolean successful;
    private CardFlipAnimator cardFlipAnimator;
    private Button doneButton;
    private EditText name, addressLine1, addressLine2, addressCity, addressState, addressZip;
    private Spinner countrySpinner;

    public static AddCardDialogFragment newInstance(final String PUBLISHABLE_KEY, String showAddress, String name, ReadableMap theme) {
        Bundle args = new Bundle();
        args.putString(KEY, PUBLISHABLE_KEY);
        args.putString(ADDRESS_KEY, showAddress);
        args.putString(NAME_KEY, name);
        if (theme != null) {
            ReadableMapKeySetIterator iterator = theme.keySetIterator(); //not implemented
//            while (iterator.hasNextKey()){
//                Log.i("TAG", "theme.keySetIterator().nextKey() >> " + iterator.nextKey());
//            }
        }
        AddCardDialogFragment fragment = new AddCardDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }


    public void setPromise(Promise promise) {
        this.promise = promise;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            PUBLISHABLE_KEY = getArguments().getString(KEY);
            String address = getArguments().getString(ADDRESS_KEY);
            SHOW_ADDRESS = address != null ? address : "";
            CARDHOLDER_NAME = getArguments().getString(NAME_KEY);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final View view = View.inflate(getActivity(), R.layout.payment_form_fragment_two, null);
        final AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.CustomAppTheme)
                .setView(view)
                .setTitle(R.string.gettipsi_card_enter_dialog_title)
                .setPositiveButton(R.string.gettipsi_card_enter_dialog_positive_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        onSaveCLick();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.show();
        doneButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onSaveCLick();
            }
        });
        doneButton.setTextColor(ContextCompat.getColor(getActivity(), R.color.colorPrimary));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(getActivity(), R.color.colorPrimary));
        doneButton.setEnabled(false);

        bindViews(view);
        init();

        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (!successful && promise != null) {
            promise.reject(TAG, getString(R.string.gettipsi_user_cancel_dialog));
            promise = null;
        }
        super.onDismiss(dialog);
    }

    private void bindViews(final View view) {
        progressBar = (ProgressBar) view.findViewById(R.id.buttonProgress);
        from = (CreditCardForm) view.findViewById(R.id.credit_card_form);
        imageFlipedCard = (ImageView) view.findViewById(R.id.imageFlippedCard);
        imageFlipedCardBack = (ImageView) view.findViewById(R.id.imageFlippedCardBack);

        LinearLayout addressView = view.findViewById(R.id.addressView);
        if (SHOW_ADDRESS.equals("full")) {
            addressView.setVisibility(View.VISIBLE);
        }
        name = view.findViewById(R.id.name);
        name.setText(CARDHOLDER_NAME);
        addressLine1 = view.findViewById(R.id.addressLine1);
        addressLine2 = view.findViewById(R.id.addressLine2);
        addressCity = view.findViewById(R.id.addressCity);
        addressState = view.findViewById(R.id.addressState);
        addressZip = view.findViewById(R.id.addressZip);

        name.setTextColor(Color.WHITE);
        addressLine1.setTextColor(Color.WHITE);
        addressLine2.setTextColor(Color.WHITE);
        addressCity.setTextColor(Color.WHITE);
        addressState.setTextColor(Color.WHITE);
        addressZip.setTextColor(Color.WHITE);

        Locale[] locale = Locale.getAvailableLocales();
        ArrayList<String> countries = new ArrayList<String>();
        String country;
        for (Locale loc : locale) {
            country = loc.getDisplayCountry();
            if (country.length() > 0 && !countries.contains(country)) {
                countries.add(country);
            }
        }
        Collections.sort(countries, String.CASE_INSENSITIVE_ORDER);
        countrySpinner = view.findViewById(R.id.countrySpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_activated_1, countries);
        countrySpinner.setAdapter(adapter);
        countrySpinner.setSelection(adapter.getPosition("United States"));
    }


    private void init() {
        from.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(final View view, boolean b) {
                if (CCV_INPUT_CLASS_NAME.equals(view.getClass().getSimpleName())) {
                    if (b) {
                        cardFlipAnimator.showBack();
                        if (view.getTag() == null) {
                            view.setTag("TAG");
                            ((SecurityCodeText) view).addTextChangedListener(new TextWatcher() {
                                @Override
                                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                                    //unused
                                }

                                @Override
                                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                                    doneButton.setEnabled(charSequence.length() >= 3);
                                }

                                @Override
                                public void afterTextChanged(Editable editable) {
                                    //unused
                                }
                            });
                        }
                    } else {
                        cardFlipAnimator.showFront();
                    }
                }

            }
        });

        cardFlipAnimator = new CardFlipAnimator(getActivity(), imageFlipedCard, imageFlipedCardBack);
        successful = false;
    }

    public void onSaveCLick() {
        if (SHOW_ADDRESS.equals("full")) {
            if (!isValidAddress()) {
                return;
            }
        }
        doneButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        final CreditCard fromCard = from.getCreditCard();
        final Card card = new Card(
                fromCard.getCardNumber(),
                fromCard.getExpMonth(),
                fromCard.getExpYear(),
                fromCard.getSecurityCode());
        if (SHOW_ADDRESS.equals("full")) {
            card.setName(name.getText().toString());
            card.setAddressLine1(addressLine1.getText().toString());
            card.setAddressLine2(addressLine2.getText().toString().trim().length() > 0 ? addressLine2.getText().toString() : "");
            card.setAddressCity(addressCity.getText().toString());
            card.setAddressState(addressState.getText().toString());
            card.setAddressZip(addressZip.getText().toString());
            card.setAddressCountry(countrySpinner.getSelectedItem().toString());
        }
        String errorMessage = Utils.validateCard(card);
        if (errorMessage == null) {
            StripeModule.getInstance().getStripe().createToken(
                    card,
                    PUBLISHABLE_KEY,
                    new TokenCallback() {
                        public void onSuccess(Token token) {
                            final WritableMap newToken = Arguments.createMap();
                            newToken.putString("tokenId", token.getId());
                            newToken.putBoolean("livemode", token.getLivemode());
                            newToken.putDouble("created", token.getCreated().getTime());
                            newToken.putBoolean("user", token.getUsed());
                            final WritableMap cardMap = Arguments.createMap();
                            final Card card = token.getCard();
                            cardMap.putString("cardId", card.getId());
                            cardMap.putString("brand", card.getBrand());
                            cardMap.putString("last4", card.getLast4());
                            cardMap.putInt("expMonth", card.getExpMonth());
                            cardMap.putInt("expYear", card.getExpYear());
                            cardMap.putString("country", card.getCountry());
                            cardMap.putString("currency", card.getCurrency());
                            cardMap.putString("name", card.getName());
                            cardMap.putString("addressLine1", card.getAddressLine1());
                            cardMap.putString("addressLine2", card.getAddressLine2());
                            cardMap.putString("addressCity", card.getAddressCity());
                            cardMap.putString("addressState", card.getAddressState());
                            cardMap.putString("addressCountry", card.getAddressCountry());
                            cardMap.putString("addressZip", card.getAddressZip());
                            newToken.putMap("card", cardMap);
                            if (promise != null) {
                                promise.resolve(newToken);
                                promise = null;
                            }
                            successful = true;
                            dismiss();
                        }

                        public void onError(Exception error) {
                            doneButton.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            showToast(error.getLocalizedMessage());
                        }
                    });
        } else {
            doneButton.setEnabled(true);
            progressBar.setVisibility(View.GONE);
            showToast(errorMessage);
        }
    }

    private boolean isValidAddress() {
        String error;
        if (TextUtils.isEmpty(name.getText().toString())) {
            error = "Name is required";
            name.setError(error);
            showToast(error);
            return false;
        }
        if (TextUtils.isEmpty(addressLine1.getText().toString())) {
            error = "Address is required";
            addressLine1.setError(error);
            showToast(error);
            return false;
        }
        if (TextUtils.isEmpty(addressCity.getText().toString())) {
            error = "City name is required";
            addressCity.setError(error);
            showToast(error);
            return false;
        }
        if (TextUtils.isEmpty(addressState.getText().toString())) {
            error = "State name is required";
            addressState.setError(error);
            showToast(error);
            return false;
        }
        if (TextUtils.isEmpty(addressZip.getText().toString())) {
            error = "Zipcode is required";
            addressZip.setError(error);
            showToast(error);
            return false;
        }
        if (countrySpinner.getSelectedItem().toString().equals("United States")) {
            String US_ZIP_REGEX = "\\d{5}([ \\-]\\d{4})?";
            if (!addressZip.getText().toString().matches(US_ZIP_REGEX)) {
                error = "Zipcode is not valid";
                addressZip.setError(error);
                showToast(error);
                return false;
            }
        }
        return true;
    }

    public void showToast(String message) {
        Context context = getActivity();
        if (context != null && !TextUtils.isEmpty(message)) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }
}
