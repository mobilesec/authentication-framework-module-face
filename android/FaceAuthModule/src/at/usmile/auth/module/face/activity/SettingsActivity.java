package at.usmile.auth.module.face.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import at.usmile.auth.module.face.R;
import at.usmile.panshot.SharedPrefs;
import at.usmile.panshot.util.DataUtil;

/**
 * Allows user to change some authentication settings.
 * 
 * @author Rainhard Findling
 * @date 7 Apr 2015
 * @version 1
 */
public class SettingsActivity extends Activity {

	private final String TAG = "SettingsActivity";
	/** if we should ask user about really changing settings before doing so. */
	private boolean mAskBeforeSettingsChange = true;

	@Override
	protected void onResume() {
		super.onResume();
		mAskBeforeSettingsChange = true;
	}

	@Override
	protected void onCreate(Bundle _savedInstanceState) {
		super.onCreate(_savedInstanceState);
		Log.d(TAG, "onCreate()");

		setContentView(R.layout.layout_activity_face_settings);

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
					reallyChangeSettingsDialog(new Runnable() {
						public void run() {
							int val = Integer.parseInt(edittextFaceSize.getText().toString());
							getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
									.putInt(SharedPrefs.FACE_SIZE, val).commit();
						}
					});
				} catch (NumberFormatException e) {
				} catch (NullPointerException e) {
				}
			}
		});

		// USE FRONTAL ONLY
		CompoundButton compoundButtonUseFrontalOnly = (CompoundButton) findViewById(R.id.switchUseFrontalOnly);
		compoundButtonUseFrontalOnly.setChecked(SharedPrefs.isFrontalOnly(this));
		compoundButtonUseFrontalOnly.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton _buttonView, final boolean _isChecked) {
				reallyChangeSettingsDialog(new Runnable() {
					public void run() {
						Log.d(TAG, "switchUseFrontalOnly#onCheckedChanged(): " + _isChecked);
						getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
								.putBoolean(SharedPrefs.USE_FRONTAL_ONLY, _isChecked).commit();
					}
				});
			}
		});

		// KNN K
		final EditText edittextKnnK = (EditText) findViewById(R.id.editTextKnnK);
		edittextKnnK.setText("" + SharedPrefs.getKnnK(this));
		edittextKnnK.setEnabled(SharedPrefs.useKnn(this));
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
					reallyChangeSettingsDialog(new Runnable() {
						public void run() {
							int val = Integer.parseInt(edittextKnnK.getText().toString());
							getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
									.putInt(SharedPrefs.KNN_K, val).commit();
						}
					});
				} catch (NumberFormatException e) {
				} catch (NullPointerException e) {
				}
			}
		});

		// RADIOGROUP CLASSIFIER TYPE
		final RadioGroup rGroupClassifierType = (RadioGroup) findViewById(R.id.radioGroupClassierType);
		final RadioButton radiobuttonKnn = (RadioButton) findViewById(R.id.radioKnn);
		final RadioButton radiobuttonSvm = (RadioButton) findViewById(R.id.radioSvm);
		radiobuttonKnn.setChecked(SharedPrefs.useKnn(this));
		radiobuttonSvm.setChecked(!SharedPrefs.useKnn(this));
		rGroupClassifierType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			public void onCheckedChanged(final RadioGroup rGroup, final int checkedId) {
				reallyChangeSettingsDialog(new Runnable() {
					public void run() {
						RadioButton checkedRadioButton = (RadioButton) rGroup.findViewById(checkedId);
						getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
								.putBoolean(SharedPrefs.USE_CLASSIFIER_TYPE_KNN, checkedRadioButton == radiobuttonKnn).commit();
						edittextKnnK.setEnabled(checkedRadioButton == radiobuttonKnn);
					}
				});
			}
		});

		// ENERGY NORMALIZATION KERNEL SUBSAMPLING
		final EditText edittextEnergyNormalizationKernelSubsamplingFactor = (EditText) findViewById(R.id.editTextImageEnergyNormalizationKernelSubsampling);
		edittextEnergyNormalizationKernelSubsamplingFactor.setText(""
				+ SharedPrefs.getImageEnergyNormalizationSubsamplingFactor(this));
		edittextEnergyNormalizationKernelSubsamplingFactor.setEnabled(SharedPrefs.useImageEnergyNormlization(this));
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
					reallyChangeSettingsDialog(new Runnable() {
						public void run() {
							float factor = Float.parseFloat(edittextEnergyNormalizationKernelSubsamplingFactor.getText()
									.toString());
							getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
									.putFloat(SharedPrefs.ENERGY_NORMALIZATION_KERNEL_SUBSAMPLING_FACTOR, factor).commit();
						}
					});
				} catch (NumberFormatException e) {
				} catch (NullPointerException e) {
				}
			}
		});

		// USE ENERGY NORMALIZATION
		final CompoundButton compoundButtonUseImageEnergyNormalization = (CompoundButton) findViewById(R.id.checkBoxUseImageEnergyNormalization);
		compoundButtonUseImageEnergyNormalization.setChecked(SharedPrefs.useImageEnergyNormlization(this));
		compoundButtonUseImageEnergyNormalization.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton _buttonView, final boolean _isChecked) {
				reallyChangeSettingsDialog(new Runnable() {
					public void run() {
						getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
								.putBoolean(SharedPrefs.USE_ENERGY_NORMALIZATION, _isChecked).commit();
						edittextEnergyNormalizationKernelSubsamplingFactor.setEnabled(_isChecked);
					}
				});
			}
		});

		// AMOUNT OF PCA FEATURES
		final EditText edittextPcaFeatures = (EditText) findViewById(R.id.editTextPcaFeatures);
		edittextPcaFeatures.setText("" + SharedPrefs.getAmountOfPcaFeatures(this));
		edittextPcaFeatures.setEnabled(SharedPrefs.usePca(this));
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
					reallyChangeSettingsDialog(new Runnable() {
						public void run() {
							int val = Integer.parseInt(edittextPcaFeatures.getText().toString());
							getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
									.putInt(SharedPrefs.PCA_AMOUNT_OF_FEATURES, val).commit();
						}
					});
				} catch (NumberFormatException e) {
				} catch (NullPointerException e) {
				}
			}
		});

		// USE PCA
		CompoundButton compoundButtonUsePca = (CompoundButton) findViewById(R.id.checkBoxUsePca);
		compoundButtonUsePca.setChecked(SharedPrefs.usePca(this));
		compoundButtonUsePca.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(final CompoundButton _buttonView, final boolean _isChecked) {
				reallyChangeSettingsDialog(new Runnable() {
					public void run() {
						getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
								.putBoolean(SharedPrefs.USE_PCA, _isChecked).commit();
						edittextPcaFeatures.setEnabled(_isChecked);
					}
				});
			}
		});

	}

	public void reallyChangeSettingsDialog(final Runnable _r) {
		if (!mAskBeforeSettingsChange) {
			_r.run();
			return;
		}
		// "are you sure" dialogue box
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {

					case DialogInterface.BUTTON_POSITIVE:
						Log.d(ManageDataActivity.class.getSimpleName(), "dialogClickListener.YES");
						// delete classifiers
						DataUtil.deleteClassifiers(SettingsActivity.this);
						mAskBeforeSettingsChange = false;
						// change settings
						_r.run();
						break;

					case DialogInterface.BUTTON_NEGATIVE:
						Log.d(ManageDataActivity.class.getSimpleName(), "dialogClickListener.NO");
						SettingsActivity.this.finish();
						break;
				}
			}

		};
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(getResources().getString(R.string.really_change_settings))
				.setPositiveButton(getResources().getString(R.string.yes), dialogClickListener)
				.setNegativeButton(getResources().getString(R.string.no), dialogClickListener).show();
	}
}
