package at.usmile.auth.module.face.activity;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import at.usmile.auth.module.face.R;
import at.usmile.panshot.SharedPrefs;

/**
 * Allows user to change some authentication settings.
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class SettingsActivity extends Activity {
	// TODO onChange of certain settings: notify user and delete classifiers
	// TODO think of case in which training is ongoing (lock settings for that
	// time?)

	private final String TAG = "SettingsActivity";

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.layout_activity_face_settings);

		// RADIOGROUP CLASSIFIER TYPE
		final RadioGroup rGroupClassifierType = (RadioGroup) findViewById(R.id.radioGroupClassierType);
		final RadioButton radiobuttonKnn = (RadioButton) findViewById(R.id.radioKnn);
		final RadioButton radiobuttonSvm = (RadioButton) findViewById(R.id.radioSvm);
		boolean useKnn = SharedPrefs.useKnn(this);
		radiobuttonKnn.setChecked(useKnn);
		radiobuttonSvm.setChecked(!useKnn);
		rGroupClassifierType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(RadioGroup rGroup, int checkedId) {
				RadioButton checkedRadioButton = (RadioButton) rGroup.findViewById(checkedId);
				getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
						.putBoolean(SharedPrefs.USE_CLASSIFIER_TYPE_KNN, checkedRadioButton == radiobuttonKnn).commit();
			}
		});

		// USE ENERGY NORMALIZATION
		CheckBox checkboxUseImageEnergyNormalization = (CheckBox) findViewById(R.id.checkBoxUseImageEnergyNormalization);
		checkboxUseImageEnergyNormalization.setChecked(SharedPrefs.useImageEnergyNormlization(this));
		checkboxUseImageEnergyNormalization.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton _buttonView, boolean _isChecked) {
				getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
						.putBoolean(SharedPrefs.USE_ENERGY_NORMALIZATION, _isChecked).commit();
			}
		});

		// ENERGY NORMALIZATION KERNEL SUBSAMPLING
		final EditText edittextEnergyNormalizationKernelSubsamplingFactor = (EditText) findViewById(R.id.editTextImageEnergyNormalizationKernelSubsampling);
		edittextEnergyNormalizationKernelSubsamplingFactor.setText(""
				+ SharedPrefs.getImageEnergyNormalizationSubsamplingFactor(this));
		edittextEnergyNormalizationKernelSubsamplingFactor.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence _s, int _start, int _before, int _count) {
			}

			@Override
			public void beforeTextChanged(CharSequence _s, int _start, int _count, int _after) {
			}

			@Override
			public void afterTextChanged(Editable _s) {
				try {
					float factor = Float.parseFloat(edittextEnergyNormalizationKernelSubsamplingFactor.getText().toString());
					getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
							.putFloat(SharedPrefs.ENERGY_NORMALIZATION_KERNEL_SUBSAMPLING_FACTOR, factor).commit();
				} catch (NumberFormatException e) {
				} catch (NullPointerException e) {
				}
			}
		});

		// KNN K
		final EditText edittextKnnK = (EditText) findViewById(R.id.editTextKnnK);
		edittextKnnK.setText("" + SharedPrefs.getKnnK(this));
		edittextKnnK.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence _s, int _start, int _before, int _count) {
			}

			@Override
			public void beforeTextChanged(CharSequence _s, int _start, int _count, int _after) {
			}

			@Override
			public void afterTextChanged(Editable _s) {
				try {
					int val = Integer.parseInt(edittextKnnK.getText().toString());
					getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
							.putInt(SharedPrefs.KNN_K, val).commit();
				} catch (NumberFormatException e) {
				} catch (NullPointerException e) {
				}
			}
		});

		// AMOUNT OF PCA FEATURES
		final EditText edittextPcaFeatures = (EditText) findViewById(R.id.editTextPcaFeatures);
		edittextPcaFeatures.setText("" + SharedPrefs.getAmountOfPcaFeatures(this));
		edittextPcaFeatures.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence _s, int _start, int _before, int _count) {
			}

			@Override
			public void beforeTextChanged(CharSequence _s, int _start, int _count, int _after) {
			}

			@Override
			public void afterTextChanged(Editable _s) {
				try {
					int val = Integer.parseInt(edittextPcaFeatures.getText().toString());
					getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
							.putInt(SharedPrefs.PCA_AMOUNT_OF_FEATURES, val).commit();
				} catch (NumberFormatException e) {
				} catch (NullPointerException e) {
				}
			}
		});

		// FACE SIZE
		final EditText edittextFaceSize = (EditText) findViewById(R.id.editTextFaceSize);
		edittextFaceSize.setText("" + SharedPrefs.getFaceWidth(this));
		edittextFaceSize.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence _s, int _start, int _before, int _count) {
			}

			@Override
			public void beforeTextChanged(CharSequence _s, int _start, int _count, int _after) {
			}

			@Override
			public void afterTextChanged(Editable _s) {
				try {
					int val = Integer.parseInt(edittextFaceSize.getText().toString());
					getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
							.putInt(SharedPrefs.FACE_SIZE, val).commit();
				} catch (NumberFormatException e) {
				} catch (NullPointerException e) {
				}
			}
		});

		// USE PCA
		CheckBox checkboxUsePca = (CheckBox) findViewById(R.id.checkBoxUsePca);
		checkboxUsePca.setChecked(SharedPrefs.usePca(this));
		checkboxUsePca.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton _buttonView, boolean _isChecked) {
				getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
						.putBoolean(SharedPrefs.USE_PCA, _isChecked).commit();
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

}
