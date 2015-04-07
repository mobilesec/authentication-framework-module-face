package at.usmile.auth.module.face.activity;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.samples.facedetect.DetectionBasedTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import at.usmile.auth.module.face.PanshotImage;
import at.usmile.auth.module.face.PhotoGyroListener;
import at.usmile.auth.module.face.R;
import at.usmile.auth.module.face.SensorComponent;
import at.usmile.auth.module.face.SensorValues;
import at.usmile.auth.module.face.SensorValues.Observation;
import at.usmile.auth.module.face.SharedPrefs;
import at.usmile.auth.module.face.User;
import at.usmile.functional.Fun;
import at.usmile.functional.FunFilter;
import at.usmile.functional.FunUtil;
import at.usmile.panshot.genericobserver.GenericObservable;
import at.usmile.panshot.genericobserver.GenericObserver;
import at.usmile.panshot.recognition.PCAUtil;
import at.usmile.panshot.recognition.TrainingData;
import at.usmile.panshot.recognition.knn.DistanceMetric;
import at.usmile.panshot.recognition.knn.KnnClassifier;
import at.usmile.panshot.recognition.svm.SvmClassifier;
import at.usmile.panshot.util.MediaSaveUtil;
import at.usmile.panshot.util.PanshotUtil;
import at.usmile.tuple.GenericTuple2;
import at.usmile.tuple.GenericTuple3;
import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Preconditions;

public class OldMainActivity extends FragmentActivity implements ActionBar.TabListener {

	public static final String SESSION_ID = "01";
	/**
	 * The {@link android.support.v4.view.PagerAdapter} that will provide
	 * fragments for each of the sections. We use a
	 * {@link android.support.v4.app.FragmentPagerAdapter} derivative, which
	 * will keep every loaded fragment in memory. If this becomes too memory
	 * intensive, it may be best to switch to a
	 * {@link android.support.v4.app.FragmentStatePagerAdapter}.
	 */
	SectionsPagerAdapter mSectionsPagerAdapter;

	/** The {@link ViewPager} that will host the section contents. */
	ViewPager mViewPager;

	/** user currently selected. only used in training mode. */
	User mCurrentUser = null;

	protected static enum FragementUpdateType {
		ReloadUi, SwitchToRecognition, SwitchToTraining, ToggleRecording
	}

	// OpenCV settings
	private static final String TAG = "OCVSample::Activity";
	private static final Scalar FACE_RECT_COLOR = new Scalar(0, 255, 0, 255);
	public static final int JAVA_DETECTOR = 0;
	public static final int NATIVE_DETECTOR = 1;

	private static enum ClassifierMode {
		Training, Recognition
	}

	/**
	 * States which recognitions are possible for a) creating+training
	 * classifiers from FS-data and b) classifying new pan shot input.
	 */
	public static enum RecognitionType {
		SVM, KNN
	}

	/**
	 * Which recognition gets used when a) creating+training classifiers from
	 * FS-data and b) when classifying new pan shot input.
	 */
	private ClassifierMode mClassifierMode = ClassifierMode.Training;

	private CascadeClassifier mJavaDetector_LBPCascadeFrontalface;
	private DetectionBasedTracker mNativeDetector_LBPCascadeFrontalface;

	private CascadeClassifier mJavaDetector_LBPCascadeProfileface;
	private DetectionBasedTracker mNativeDetector_LBPCascadeProfileface;

	private CascadeClassifier mJavaDetector_HaarCascadeProfileface;
	private DetectionBasedTracker mNativeDetector_HaarCascadeProfileface;

	private int mDetectorType = JAVA_DETECTOR;
	private String[] mDetectorName;

	private CameraBridgeViewBase mOpenCvCameraView;

	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			Log.i(TAG, "BaseLoaderCallback.onManagerConnected()");
			switch (status) {
				case LoaderCallbackInterface.SUCCESS: {
					Log.i(TAG, "OpenCV loaded successfully");

					// Load native library after(!) OpenCV initialization
					System.loadLibrary("detection_based_tracker");

					try {
						// load cascade file from application resources
						File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);

						// LBPCASCADE FRONTALFACE
						InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
						File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
						FileOutputStream os = new FileOutputStream(cascadeFile);
						byte[] buffer = new byte[4096];
						int bytesRead = 0;
						while ((bytesRead = is.read(buffer)) != -1) {
							os.write(buffer, 0, bytesRead);
						}
						is.close();
						os.close();
						mJavaDetector_LBPCascadeFrontalface = new CascadeClassifier(cascadeFile.getAbsolutePath());
						if (mJavaDetector_LBPCascadeFrontalface.empty()) {
							Log.e(TAG, "Failed to load cascade classifier from " + cascadeFile.getAbsolutePath());
							mJavaDetector_LBPCascadeFrontalface = null;
						} else
							Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());

						mNativeDetector_LBPCascadeFrontalface = new DetectionBasedTracker(cascadeFile.getAbsolutePath(), 0);

						// LBPCASCADE PROFILEFACE
						is = getResources().openRawResource(R.raw.lbpcascade_profileface);
						cascadeFile = new File(cascadeDir, "lbpcascade_profileface.xml");
						os = new FileOutputStream(cascadeFile);
						buffer = new byte[4096];
						bytesRead = 0;
						while ((bytesRead = is.read(buffer)) != -1) {
							os.write(buffer, 0, bytesRead);
						}
						is.close();
						os.close();
						mJavaDetector_LBPCascadeProfileface = new CascadeClassifier(cascadeFile.getAbsolutePath());
						if (mJavaDetector_LBPCascadeProfileface.empty()) {
							Log.e(TAG, "Failed to load cascade classifier from " + cascadeFile.getAbsolutePath());
							mJavaDetector_LBPCascadeProfileface = null;
						} else
							Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());

						mNativeDetector_LBPCascadeProfileface = new DetectionBasedTracker(cascadeFile.getAbsolutePath(), 0);

						// HAARCASCADE PROFILEFACE
						is = getResources().openRawResource(R.raw.haarcascade_profileface);
						cascadeFile = new File(cascadeDir, "haarcascade_profileface.xml");
						os = new FileOutputStream(cascadeFile);
						buffer = new byte[4096];
						bytesRead = 0;
						while ((bytesRead = is.read(buffer)) != -1) {
							os.write(buffer, 0, bytesRead);
						}
						is.close();
						os.close();
						mJavaDetector_HaarCascadeProfileface = new CascadeClassifier(cascadeFile.getAbsolutePath());
						if (mJavaDetector_HaarCascadeProfileface.empty()) {
							Log.e(TAG, "Failed to load cascade classifier from " + cascadeFile.getAbsolutePath());
							mJavaDetector_HaarCascadeProfileface = null;
						} else
							Log.i(TAG, "Loaded cascade classifier from " + cascadeFile.getAbsolutePath());

						mNativeDetector_HaarCascadeProfileface = new DetectionBasedTracker(cascadeFile.getAbsolutePath(), 0);

						cascadeDir.delete();

					} catch (IOException e) {
						e.printStackTrace();
						Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
					}

					mOpenCvCameraView.enableView();
				}
					break;
				default: {
					super.onManagerConnected(status);
				}
					break;
			}
		}
	};

	protected void notifyFragments(GenericObservable<OldMainActivity.FragementUpdateType> observable,
			OldMainActivity.FragementUpdateType notification) {
		// check if we're interested in that update too
		switch (notification) {
			case SwitchToRecognition:
				mClassifierMode = ClassifierMode.Recognition;
				Log.i(TAG, "application switched to recognition mode.");
				break;
			case SwitchToTraining:
				mClassifierMode = ClassifierMode.Training;
				Log.i(TAG, "application switched to training mode.");
				break;
			case ReloadUi:
			default:
				// not interested
				break;
		}
		// notify fragments
		Log.i(TAG, "notifyFragments(): fragments=" + getSupportFragmentManager().getFragments());
		for (Fragment f : getSupportFragmentManager().getFragments()) {
			// the fragment handling is not dope properly, therefore the next
			// line can throw exception
			// therefore fix fragment handling sometimes
			((GenericObserver<OldMainActivity.FragementUpdateType>) f).update(observable, notification);
		}
	}

	public OldMainActivity() {
		mDetectorName = new String[2];
		mDetectorName[JAVA_DETECTOR] = "Java";
		mDetectorName[NATIVE_DETECTOR] = "Native (tracking)";
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
		setContentView(R.layout.layout_activity_oldmain);

		// Set up the action bar.
		final ActionBar actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

		// Set up the ViewPager with the sections adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mSectionsPagerAdapter);

		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				actionBar.setSelectedNavigationItem(position);
			}
		});

		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by
			// the adapter. Also specify this Activity object, which implements
			// the TabListener interface, as the callback (listener) for when
			// this tab is selected.
			actionBar.addTab(actionBar.newTab().setText(mSectionsPagerAdapter.getPageTitle(i)).setTabListener(this));
		}

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		mViewPager.setCurrentItem(tab.getPosition());
	}

	@Override
	protected void onResume() {
		super.onResume();
		mViewPager.setCurrentItem(0);
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.v(OldMainActivity.class.getSimpleName(), event.toString());
		// if (mViewPager.getCurrentItem() == 1) {
		// we're in the recording tab
		switch (keyCode) {
			case KeyEvent.KEYCODE_VOLUME_DOWN:
			case KeyEvent.KEYCODE_VOLUME_UP:
				Log.d(OldMainActivity.class.getSimpleName(), "keyodwn: vol down/up detected");
				// .
				// current
				// viewpager
				// item="
				// +
				// mViewPager.getCurrentItem());
				// Fragment
				// fragment
				// =
				// getSupportFragmentManager().findFragmentById(0);
				// Log.i(TAG, "camerafragment=" + fragment);
				// CameraFragment cf = (CameraFragment) fragment;
				// Log.d(MainActivity.class.getSimpleName(),
				// "onKeyDown: camerafragment=" + cf);
				// cf.toggleTakingPictures();
				notifyFragments(null, FragementUpdateType.ToggleRecording);
				return true;
			default:
				return false;
		}
		// }
		// return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		Log.v(OldMainActivity.class.getSimpleName(), event.toString());
		if (mViewPager.getCurrentItem() == 1) {
			// we're in the recording tab
			switch (keyCode) {
				case KeyEvent.KEYCODE_VOLUME_DOWN:
				case KeyEvent.KEYCODE_VOLUME_UP:
					return true;
				default:
					return false;
			}
		}
		return false;
	}

	/**
	 * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
	 * one of the sections/tabs/pages.
	 */
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		public SectionsPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			// getItem is called to instantiate the fragment for the given page.
			Fragment fragment = null;
			Log.e(OldMainActivity.class.getSimpleName(), "SectionsPagerAdapter.getItem: creating new fragment for pos="
					+ position);
			switch (position) {
				case 0:
					fragment = new SettingsFragment();
					break;
				case 1:
					fragment = new CameraFragment();
					break;
				default:
					throw new RuntimeException("MainActivity.getItem(): position " + position + " not implemented.");
			}
			return fragment;
		}

		@Override
		public int getCount() {
			// Show N total pages.
			return 2;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			Locale l = Locale.getDefault();
			switch (position) {
				case 0:
					return getString(R.string.title_section1).toUpperCase(l);
				case 1:
					return getString(R.string.title_section2).toUpperCase(l);
			}
			return null;
		}
	}

	public static class SettingsFragment extends Fragment implements GenericObserver<OldMainActivity.FragementUpdateType> {

		private List<User> users = new ArrayList<User>();

		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";

		private ArrayAdapter<String> spinnerAdapterUsers;

		private Spinner spinnerIdentity;

		public SettingsFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			final View view = inflater.inflate(R.layout.layout_fragment_main_settings, container, false);

			spinnerIdentity = (Spinner) view.findViewById(R.id.spinner_identity);
			Preconditions.checkNotNull(spinnerIdentity, "spinner not found");
			spinnerAdapterUsers = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item,
					android.R.id.text1);
			// Specify the layout to use when the list of choices appears
			spinnerAdapterUsers.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
			// Apply the adapter to the spinner
			spinnerIdentity.setAdapter(spinnerAdapterUsers);
			spinnerIdentity.setOnItemSelectedListener(new OnItemSelectedListener() {

				@Override
				public void onItemSelected(AdapterView<?> _arg0, View _arg1, int _arg2, long _arg3) {
					Log.d(OldMainActivity.class.getSimpleName(), "spinner.onItemSelected()");
					((OldMainActivity) getActivity()).mCurrentUser = users.get(_arg2);
					((OldMainActivity) getActivity()).notifyFragments(null, OldMainActivity.FragementUpdateType.ReloadUi);
				}

				@Override
				public void onNothingSelected(AdapterView<?> _arg0) {
					Log.d(OldMainActivity.class.getSimpleName(), "spinner.onNothingSelected()");
					((OldMainActivity) getActivity()).mCurrentUser = null;
				}
			});
			// RADIOGROUP TRAINING CLASSIFICATION
			final RadioGroup rGroupTrainingClassification = (RadioGroup) view.findViewById(R.id.radioGroupTrainingRecognition);
			final RadioButton radiobuttonTraining = (RadioButton) view.findViewById(R.id.radioTraining);
			// final RadioButton radiobuttonRecognition = (RadioButton)
			// view.findViewById(R.id.radioRecognition);
			rGroupTrainingClassification.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
				public void onCheckedChanged(RadioGroup rGroup, int checkedId) {
					RadioButton checkedRadioButton = (RadioButton) rGroup.findViewById(checkedId);
					if (checkedRadioButton == radiobuttonTraining) {
						((OldMainActivity) getActivity()).notifyFragments(null,
								OldMainActivity.FragementUpdateType.SwitchToTraining);
					} else {
						// train classifiers
						((OldMainActivity) getActivity()).notifyFragments(null,
								OldMainActivity.FragementUpdateType.SwitchToRecognition);
					}
				}
			});
			// RADIOGROUP CLASSIFIER TYPE
			final RadioGroup rGroupClassifierType = (RadioGroup) view.findViewById(R.id.radioGroupClassierType);
			final RadioButton radiobuttonKnn = (RadioButton) view.findViewById(R.id.radioKnn);
			final RadioButton radiobuttonSvm = (RadioButton) view.findViewById(R.id.radioSvm);
			boolean useKnn = SharedPrefs.useKnn(getActivity());
			radiobuttonKnn.setChecked(useKnn);
			radiobuttonSvm.setChecked(!useKnn);
			rGroupClassifierType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
				public void onCheckedChanged(RadioGroup rGroup, int checkedId) {
					RadioButton checkedRadioButton = (RadioButton) rGroup.findViewById(checkedId);
					getActivity().getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
							.putBoolean(SharedPrefs.USE_CLASSIFIER_TYPE_KNN, checkedRadioButton == radiobuttonKnn).commit();
				}
			});
			// NEW USER
			final Button buttonNewUser = (Button) view.findViewById(R.id.button_new_user);
			buttonNewUser.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					Log.d(OldMainActivity.class.getSimpleName(), "buttonNewUser.onClick()");
					final EditText editTextNewUser = (EditText) view.findViewById(R.id.edit_text_new_user);
					Preconditions.checkNotNull(editTextNewUser, "editTextNewUser not found.");
					String userName = editTextNewUser.getText().toString();
					if (userName.equals("")) {
						Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.toast_username_empty),
								Toast.LENGTH_SHORT).show();
						return;
					}
					if (userName.contains("_") || userName.contains("/")) {
						Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.invalid_username),
								Toast.LENGTH_SHORT).show();
						return;
					}
					try {
						getUserForName(userName); // fails if user does not
													// exist yet
						Log.d(OldMainActivity.class.getSimpleName(), userName + " already used, choose another one.");
						Toast.makeText(getActivity(),
								getActivity().getResources().getString(R.string.toast_username_already_taken), Toast.LENGTH_SHORT)
								.show();
					} catch (RuntimeException e) {
						// user does not exist yet
						User newUser = createNewUser(userName);
						users.add(newUser);
						updateUiWithExistingUsers();
						Log.d(OldMainActivity.class.getSimpleName(), "generated user: " + newUser);
					}
				}
			});
			// USE ENERGY NORMALIZATION
			CheckBox checkboxUseImageEnergyNormalization = (CheckBox) view.findViewById(R.id.checkBoxUseImageEnergyNormalization);
			checkboxUseImageEnergyNormalization.setChecked(SharedPrefs.useImageEnergyNormlization(getActivity()));
			checkboxUseImageEnergyNormalization.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton _buttonView, boolean _isChecked) {
					getActivity().getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
							.putBoolean(SharedPrefs.USE_ENERGY_NORMALIZATION, _isChecked).commit();
				}
			});
			// ENERGY NORMALIZATION KERNEL SUBSAMPLING
			final EditText edittextEnergyNormalizationKernelSubsamplingFactor = (EditText) view
					.findViewById(R.id.editTextImageEnergyNormalizationKernelSubsampling);
			edittextEnergyNormalizationKernelSubsamplingFactor.setText(""
					+ SharedPrefs.getImageEnergyNormalizationSubsamplingFactor(getActivity()));
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
						getActivity().getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
								.putFloat(SharedPrefs.ENERGY_NORMALIZATION_KERNEL_SUBSAMPLING_FACTOR, factor).commit();
					} catch (NumberFormatException e) {
					} catch (NullPointerException e) {
					}
				}
			});
			// KNN K
			final EditText edittextKnnK = (EditText) view.findViewById(R.id.editTextKnnK);
			edittextKnnK.setText("" + SharedPrefs.getKnnK(getActivity()));
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
						getActivity().getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
								.putInt(SharedPrefs.KNN_K, val).commit();
					} catch (NumberFormatException e) {
					} catch (NullPointerException e) {
					}
				}
			});

			// AMOUNT OF PCA FEATURES
			final EditText edittextPcaFeatures = (EditText) view.findViewById(R.id.editTextPcaFeatures);
			edittextPcaFeatures.setText("" + SharedPrefs.getAmountOfPcaFeatures(getActivity()));
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
						getActivity().getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
								.putInt(SharedPrefs.PCA_AMOUNT_OF_FEATURES, val).commit();
					} catch (NumberFormatException e) {
					} catch (NullPointerException e) {
					}
				}
			});

			// FACE SIZE
			final EditText edittextFaceSize = (EditText) view.findViewById(R.id.editTextFaceSize);
			edittextFaceSize.setText("" + SharedPrefs.getFaceWidth(getActivity()));
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
						getActivity().getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
								.putInt(SharedPrefs.FACE_SIZE, val).commit();
					} catch (NumberFormatException e) {
					} catch (NullPointerException e) {
					}
				}
			});

			// USE PCA
			CheckBox checkboxUsePca = (CheckBox) view.findViewById(R.id.checkBoxUsePca);
			checkboxUsePca.setChecked(SharedPrefs.usePca(getActivity()));
			checkboxUsePca.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton _buttonView, boolean _isChecked) {
					getActivity().getSharedPreferences(SharedPrefs.SHARED_PREFENCES_ID, Context.MODE_PRIVATE).edit()
							.putBoolean(SharedPrefs.USE_PCA, _isChecked).commit();
				}
			});
			return view;
		}

		@Override
		public void onResume() {
			super.onResume();
			loadUsersAndUpdateUi();
		}

		private void loadUsersAndUpdateUi() {
			if (!MediaSaveUtil.isSdCardAvailableRW()) {
				Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.sd_card_not_available),
						Toast.LENGTH_LONG).show();
				return;
			}
			try {
				users = loadExistingUsers();
			} catch (NotFoundException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(),
						getActivity().getResources().getString(R.string.media_directory_not_found_exception), Toast.LENGTH_LONG)
						.show();
				return;
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.media_directory_cannot_be_created),
						Toast.LENGTH_LONG).show();
				return;
			}
			updateUiWithExistingUsers();
		}

		public void updateUiWithExistingUsers() {
			spinnerAdapterUsers.clear();
			for (User u : users) {
				spinnerAdapterUsers.add(u.getName());
			}
			spinnerAdapterUsers.notifyDataSetChanged();
		}

		public List<User> loadExistingUsers() throws NotFoundException, IOException {
			File mediaDir = MediaSaveUtil.getMediaStorageDirectory(getResources().getString(R.string.app_media_directory_name));
			// load from fs/db
			File[] files = mediaDir.listFiles();
			List<User> list = new ArrayList<User>();
			for (File inFile : files) {
				if (inFile.isDirectory()) {
					// split id / name
					String name = inFile.getName().split("_")[0];
					String id = inFile.getName().split("_")[1];
					list.add(new User(id, name));
				}
			}
			return list;
		}

		public User getUserForName(String name) {
			for (User u : users) {
				if (u.getName().equalsIgnoreCase(name)) {
					return u;
				}
			}
			throw new RuntimeException(name + ": no such user.");
		}

		public User createNewUser(String name) {
			String id = Long.toString(Math.abs(new Random(System.currentTimeMillis()).nextLong()));
			return new User(id, name);
		}

		@Override
		public void update(GenericObservable<OldMainActivity.FragementUpdateType> _o, OldMainActivity.FragementUpdateType _arg) {
			// we've set the user name, therefore ignore (have already updated)
		}
	}

	public static class CameraFragment extends Fragment implements GenericObserver<OldMainActivity.FragementUpdateType>,
			CvCameraViewListener2 {

		/**
		 * The fragment argument representing the section number for this
		 * fragment.
		 */
		public static final String ARG_SECTION_NUMBER = "section_number";

		private static final Logger LOGGER = LoggerFactory.getLogger(CameraFragment.class);

		private static final float PANSHOT_TARGET_MIN_ANGLE = (float) (Math.PI * 170.0f / 180.0f);

		private static final String CSV_FILENAME_EXTENSION = ".csv.jpg";

		/**
		 * Angle that lies between two neighbouring images of the same panshot
		 * and angle that lies between two neighbouring classifiers.
		 */
		private static final float ANGLE_DIFF_OF_PHOTOS = (float) (22.5f / 2f / 180f * Math.PI);
		// 22.5 degree = 9 pics / 180 degree
		/**
		 * why ODD x ANGLE_DIFF_OF_PHOTOS? Because as classifiers depend on
		 * ANGLE_DIFF_OF_PHOTOS this prevents classifiers of having to handle
		 * frontal AND profile images, as the separation is exactly in between.
		 */
		private static final float FRONTAL_MAX_ANGLE = 3 * ANGLE_DIFF_OF_PHOTOS;

		/**
		 * BAD: hard coded index of angle values in angle array (we are only
		 * looking at one axis here).
		 */
		private static final int ANGLE_INDEX = 0;

		/**
		 * how much images are required per perspective and user in order to
		 * allow training.
		 */
		private static final Integer MIN_AMOUNT_IMAGES_PER_SUBJECT_AND_CLASSIFIER = 3;

		/**
		 * if only frontal images are used. if yes: recording stops immediately
		 * after taking the first pic, so user does not have to press twice.
		 */
		private static final boolean USE_FRONTAL_ONLY = true;

		// ================================================================================================================
		// MEMBERS

		/** true if the media recorder currently is recording via the cam. */
		private boolean mIsTakingPictures = false;
		/**
		 * checks if it is time to take a new photo in the faceview, based on
		 * the gyro-sensor.
		 */
		private PhotoGyroListener mPhotoGyroListener = new PhotoGyroListener(ANGLE_DIFF_OF_PHOTOS);

		private List<PanshotImage> images = new ArrayList<PanshotImage>();

		private TextView textviewIdentity;

		public CameraFragment() {
			Log.i(TAG, "Instantiated new " + this.getClass());
		}

		private Mat mRgba;
		private Mat mGray;

		private float mRelativeFaceSize = 0.4f;
		private int mAbsoluteFaceSize = 0;

		/**
		 * classifiers get created when switching from training to
		 * classification and destroyed when switching back.
		 */
		private Map<Integer, SvmClassifier> mSvmClassifiers = new HashMap<Integer, SvmClassifier>();
		private Map<Integer, KnnClassifier> mKnnClassifiers = new HashMap<Integer, KnnClassifier>();

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.layout_fragment_main_recording, container, false);
			((OldMainActivity) getActivity()).mOpenCvCameraView = (CameraBridgeViewBase) view
					.findViewById(R.id.fd_activity_surface_view);
			((OldMainActivity) getActivity()).mOpenCvCameraView.setCameraIndex(1);
			// ((MainActivity)
			// getActivity()).mOpenCvCameraView.setDisplayOrientation(90);
			// ((MainActivity) getActivity()).mOpenCvCameraView.setRotation(90);
			((OldMainActivity) getActivity()).mOpenCvCameraView.setCvCameraViewListener(this);

			// create sensor stuff
			SensorComponent.init(getActivity());
			SensorComponent.instance().addObserver(mPhotoGyroListener);

			textviewIdentity = (TextView) view.findViewById(R.id.textview_identity);
			Preconditions.checkNotNull(textviewIdentity, "textviewIdentity is null.");

			return view;
		}

		@Override
		public void onResume() {
			super.onResume();
			OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, getActivity(),
					((OldMainActivity) getActivity()).mLoaderCallback);

			if (!MediaSaveUtil.isSdCardAvailableRW()) {
				Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.sd_card_not_available),
						Toast.LENGTH_LONG).show();
				return;
			}

			updateUiFromCurrentUser();

			Log.d(OldMainActivity.class.getSimpleName(), "CameraFragment.onResume()");
		}

		@Override
		public void onPause() {
			super.onPause();
			if (((OldMainActivity) getActivity()).mOpenCvCameraView != null) {
				((OldMainActivity) getActivity()).mOpenCvCameraView.disableView();
			}
			// stop taking pictures if we're still taking some by now
			stopTakingPictures();
			images.clear();
			Log.d(OldMainActivity.class.getSimpleName(), "CameraFragment.onPause()");
		}

		private void startTakingPictures() {
			if (((OldMainActivity) getActivity()).mCurrentUser == null) {
				Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.no_user_selected),
						Toast.LENGTH_SHORT).show();
				return;
			}
			Log.d(OldMainActivity.class.getSimpleName(), "starting image taking...");
			mIsTakingPictures = true;
			mPhotoGyroListener.reset();
			images.clear();
			SensorComponent.instance().start();
			PanshotUtil.playSoundfile(getActivity(), R.raw.beep);
			if (!USE_FRONTAL_ONLY) {
				ImageView imageViewRedDot = (ImageView) getActivity().findViewById(R.id.imageView_redDot);
				imageViewRedDot.setVisibility(ImageView.VISIBLE);
			}
		}

		private void stopTakingPictures() {
			Log.d(OldMainActivity.class.getSimpleName(), "stopping image taking. we recorded " + images.size() + " images.");
			mIsTakingPictures = false;
			SensorComponent.instance().stop();
			if (!USE_FRONTAL_ONLY) {
				ImageView imageViewRedDot = (ImageView) getActivity().findViewById(R.id.imageView_redDot);
				imageViewRedDot.setVisibility(ImageView.INVISIBLE);
			}
		}

		private void savePanshotImages() {
			// store images to sd card
			if (!MediaSaveUtil.isSdCardAvailableRW()) {
				Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.sd_card_not_available),
						Toast.LENGTH_SHORT).show();
				return;
			}
			File mediaDir = null;
			try {
				// ensure access to media directory
				mediaDir = MediaSaveUtil.getMediaStorageDirectory(getActivity().getResources().getString(
						R.string.app_media_directory_name));
			} catch (NotFoundException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(),
						getActivity().getResources().getString(R.string.media_directory_not_found_exception), Toast.LENGTH_LONG)
						.show();
				return;
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.media_directory_cannot_be_created),
						Toast.LENGTH_LONG).show();
				return;
			}
			Log.d(OldMainActivity.class.getSimpleName(), "mediaDir=" + mediaDir.getAbsolutePath());
			// ensure access to user's directory
			File userDir = null;
			User user = ((OldMainActivity) getActivity()).mCurrentUser;
			try {
				userDir = MediaSaveUtil.ensureDirectoryExists(mediaDir, user.getFoldername());
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.user_directory_cannot_be_created),
						Toast.LENGTH_LONG).show();
				return;
			}
			File panshotDir = null;
			String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
			try {
				panshotDir = MediaSaveUtil.ensureDirectoryExists(userDir, timestamp);
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(),
						getActivity().getResources().getString(R.string.panshot_directory_cannot_be_created), Toast.LENGTH_LONG)
						.show();
				return;
			}

			// sensor data for images
			StringBuilder textfileSb = new StringBuilder(
					"sessionid,filename,subjectname,subjectid,panshotid,imagenr,timestamp,angle1,angle2,angle3,acc1,acc2,acc3,light\n");
			float minAngle = Float.MAX_VALUE;
			float maxAngle = -Float.MAX_VALUE;
			for (int imageNr = 0; imageNr < images.size(); imageNr++) {
				PanshotImage image = images.get(imageNr);
				if (minAngle > image.angleValues[ANGLE_INDEX]) {
					minAngle = image.angleValues[ANGLE_INDEX];
				}
				if (maxAngle < image.angleValues[ANGLE_INDEX]) {
					maxAngle = image.angleValues[ANGLE_INDEX];
				}
				LOGGER.debug("image " + imageNr + ": " + image.toString() + " has channels=" + image.grayImage.channels()
						+ ", depth=" + image.grayImage.depth() + ", type=" + image.grayImage.type());
				String imageNrString = "" + imageNr;
				String filename = "" + user.getId() + "_" + timestamp + "_"
						+ ("00" + imageNrString).substring(imageNrString.length());
				File imageFile = new File(panshotDir, "/" + filename + ".jpg");
				File faceFile = new File(panshotDir, "/" + filename + "_face.jpg");
				try {
					MediaSaveUtil.saveMatToJpgFile(imageFile, image.grayImage);
					if (image.grayFace != null) {
						MediaSaveUtil.saveMatToJpgFile(faceFile, image.grayFace);
					}
				} catch (IOException e) {
					Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.image_could_not_be_saved),
							Toast.LENGTH_LONG).show();
					e.printStackTrace();
					return;
				}
				textfileSb.append(SESSION_ID + "," + filename + "," + user.getName() + "," + user.getId() + "," + timestamp + ","
						+ imageNr + "," + image.timestamp + "," + image.angleValues[0] + "," + image.angleValues[1] + ","
						+ image.angleValues[2] + "," + (image.accelerationValues == null ? null : image.accelerationValues[0])
						+ "," + (image.accelerationValues == null ? null : image.accelerationValues[1]) + ","
						+ (image.accelerationValues == null ? null : image.accelerationValues[2]) + ","
						+ (image.light == null ? null : image.light) + "\n");
			}
			// store csv file
			String filename = user.getId() + "_" + timestamp + "_images";
			try {
				MediaSaveUtil.saveTextToFile(textfileSb.toString(), panshotDir, filename + CSV_FILENAME_EXTENSION);
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(),
						getActivity().getResources().getString(R.string.could_not_save_metadata_to_file, filename),
						Toast.LENGTH_LONG).show();
			}
			// acceleration and gyroscope sensor series - separate files
			SensorValues sensorValues = SensorComponent.instance().getSensorValues();
			textfileSb = new StringBuilder("angle1,angle2,angle3,timestamp\n");
			for (Observation<Float[]> o : sensorValues.getRotationHistory()) {
				textfileSb.append(o.value[0] + "," + o.value[1] + "," + o.value[2] + "," + o.timestamp + "\n");
			}
			String filenameAngle = user.getId() + "_" + timestamp + "_panshot_angles";
			try {
				MediaSaveUtil.saveTextToFile(textfileSb.toString(), panshotDir, filenameAngle + CSV_FILENAME_EXTENSION);
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(),
						getActivity().getResources().getString(R.string.could_not_save_metadata_to_file, filenameAngle),
						Toast.LENGTH_LONG).show();
				return;
			}
			textfileSb = new StringBuilder("acc1,acc2,acc3,timestamp\n");
			for (Observation<Float[]> o : sensorValues.getAccValueHistory()) {
				textfileSb.append(o.value[0] + "," + o.value[1] + "," + o.value[2] + "," + o.timestamp + "\n");
			}
			String filenameAcceleration = user.getId() + "_" + timestamp + "_panshot_accelerations";
			try {
				MediaSaveUtil.saveTextToFile(textfileSb.toString(), panshotDir, filenameAcceleration + CSV_FILENAME_EXTENSION);
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(),
						getActivity().getResources().getString(R.string.could_not_save_metadata_to_file, filenameAcceleration),
						Toast.LENGTH_LONG).show();
				return;
			}
			// store descriptive file which has subjectid etc + link to the two
			// files for easier loading
			textfileSb = new StringBuilder("sessionid,subjectname,subjectid,panshotid,filetype,filename\n");
			textfileSb.append(SESSION_ID + "," + user.getName() + "," + user.getId() + "," + timestamp + "," + "angle" + ","
					+ filenameAngle + "\n");
			textfileSb.append(SESSION_ID + "," + user.getName() + "," + user.getId() + "," + timestamp + "," + "acceleration"
					+ "," + filenameAcceleration + "\n");
			filename = user.getId() + "_" + timestamp + "_panshot_files";
			try {
				MediaSaveUtil.saveTextToFile(textfileSb.toString(), panshotDir, filename + CSV_FILENAME_EXTENSION);
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(),
						getActivity().getResources().getString(R.string.could_not_save_metadata_to_file, filename),
						Toast.LENGTH_LONG).show();
				return;
			}
			if (USE_FRONTAL_ONLY) {
				Toast.makeText(
						getActivity(),
						getActivity().getResources().getString(R.string.frontalonly_image_saved_successfully,
								((OldMainActivity) getActivity()).mCurrentUser.getName()), Toast.LENGTH_LONG).show();
				PanshotUtil.playSoundfile(getActivity(), R.raw.pure_bell);
			} else {
				// check if images were sufficient for user feedback
				float totalAngle = maxAngle - minAngle;
				if (totalAngle < PANSHOT_TARGET_MIN_ANGLE) {
					Toast.makeText(
							getActivity(),
							getActivity().getResources().getString(R.string.panshot_images_saved_angle_too_small,
									(totalAngle / Math.PI * 180f)), Toast.LENGTH_LONG).show();
					PanshotUtil.playSoundfile(getActivity(), R.raw.whisper);
				} else {
					Toast.makeText(
							getActivity(),
							getActivity().getResources().getString(R.string.panshot_images_saved_successfully, images.size(),
									(totalAngle / Math.PI * 180f)), Toast.LENGTH_LONG).show();
					PanshotUtil.playSoundfile(getActivity(), R.raw.pure_bell);
				}
			}
		}

		private List<PanshotImage> loadTrainingData() {
			// store images to sd card
			if (!MediaSaveUtil.isSdCardAvailableRW()) {
				Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.sd_card_not_available),
						Toast.LENGTH_SHORT).show();
				return null;
			}
			File mediaDir = null;
			try {
				// ensure access to media directory
				mediaDir = MediaSaveUtil.getMediaStorageDirectory(getActivity().getResources().getString(
						R.string.app_media_directory_name));
			} catch (NotFoundException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(),
						getActivity().getResources().getString(R.string.media_directory_not_found_exception), Toast.LENGTH_LONG)
						.show();
				return null;
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(getActivity(), getActivity().getResources().getString(R.string.media_directory_cannot_be_created),
						Toast.LENGTH_LONG).show();
				return null;
			}
			Log.d(OldMainActivity.class.getSimpleName(), "mediaDir=" + mediaDir.getAbsolutePath());
			// load all images of all panshots of all users
			List<PanshotImage> panshotImages = new ArrayList<PanshotImage>();
			File[] userDirectories = mediaDir.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.isDirectory();
				}
			});
			for (File userDir : userDirectories) {
				String[] userDirNameParts = userDir.getName().split("_");
				User user = new User(userDirNameParts[1], userDirNameParts[0]);
				File[] panshotDirectories = userDir.listFiles();
				for (File panshotDir : panshotDirectories) {
					Map<Integer, PanshotImage> panshotimagesOfThisPanshot = new HashMap<Integer, PanshotImage>();
					// load angle values from CSV file
					File angleCsvFile = panshotDir.listFiles(new FilenameFilter() {
						@Override
						public boolean accept(File _dir, String _filename) {
							return _filename.endsWith("_images.csv.jpg");
						}
					})[0];
					// imagename, panshotimage
					int angleIndex = -1;
					float angleNormalizer = 0;
					try {
						InputStream csvStream = new FileInputStream(angleCsvFile);
						InputStreamReader csvStreamReader = new InputStreamReader(csvStream);
						CSVReader csvReader = new CSVReader(csvStreamReader);
						String[] line;
						// throw away the header
						csvReader.readNext();
						Float[] a1 = null;
						Float[] angleValues = null;
						while ((line = csvReader.readNext()) != null) {
							// "sessionid,filename,subjectname,subjectid,panshotid,imagenr,timestamp,angle1,angle2,angle3,acc1,acc2,acc3,light\n");
							int imageNr = Integer.parseInt(line[5]);
							angleValues = new Float[] { Float.parseFloat(line[7]), Float.parseFloat(line[8]),
									Float.parseFloat(line[9]) };
							if (a1 == null) {
								a1 = angleValues;
							}
							PanshotImage panshotImage = new PanshotImage(null, null, angleValues, null, null, Long.MAX_VALUE);
							panshotImage.rec.user = user;
							panshotimagesOfThisPanshot.put(imageNr, panshotImage);
						}
						csvReader.close(); // yeah, being lazy, we should do
											// that in finally...
						// calculate angle normaliser
						GenericTuple2<Integer, Float[]> angleIndexNormaliser = PanshotUtil.calculateAngleIndexAndNormalizer(a1,
								angleValues);
						angleIndex = angleIndexNormaliser.value1;
						angleNormalizer = angleIndexNormaliser.value2[angleIndex];
						Log.d(TAG, "angleIndex=" + angleIndex + ", angleNormalizer=" + angleNormalizer);
					} catch (IOException e) {
						e.printStackTrace();
						return null;
					}
					// load face images
					File[] images = panshotDir.listFiles(new FilenameFilter() {
						@Override
						public boolean accept(File _dir, String _filename) {
							return _filename.endsWith("_face.jpg");
						}
					});
					for (File image : images) {
						int imageNr = Integer.parseInt(image.getName().replaceAll(".jpg", "").split("_")[3]);
						PanshotImage panshotImage = panshotimagesOfThisPanshot.get(imageNr);
						Mat face = Highgui.imread(image.getAbsolutePath());
						// convert to 1 channel = gray
						Mat tmp = new Mat(face.rows(), face.cols(), face.type());
						Imgproc.cvtColor(face, tmp, Imgproc.COLOR_RGBA2GRAY);
						face = tmp;
						panshotImage.grayFace = face;
						panshotImage.angleValues[angleIndex] -= angleNormalizer;
						panshotImage.rec.angleIndex = angleIndex;
						LOGGER.debug("loaded image has channels=" + panshotImage.grayFace.channels() + ", depth="
								+ panshotImage.grayFace.depth() + ", type=" + panshotImage.grayFace.type());
						panshotImages.add(panshotImage);
					}
				}
			}
			return panshotImages;
		}

		@Override
		public void update(GenericObservable<OldMainActivity.FragementUpdateType> _o, OldMainActivity.FragementUpdateType _arg) {
			switch (_arg) {
				case ReloadUi:
					// user has changed in other fragment, update ui
					updateUiFromCurrentUser();
					break;

				case ToggleRecording:
					if (!mIsTakingPictures) {
						startTakingPictures();
					} else {
						stopTakingPictures();
						// decide what to do with the images
						if (images.size() > 0) {
							// do face detection for each image
							detectFacesInRecordedImagesDependingOnAngle();
							switch (((OldMainActivity) getActivity()).mClassifierMode) {
								case Training:
									// save images
									savePanshotImages();
									break;

								case Recognition:
									// only use images in which faces where
									// detected
									List<PanshotImage> imagesWithFaces = FunUtil.filter(images, new FunFilter<PanshotImage>() {
										@Override
										public boolean filter(PanshotImage _t) {
											return _t.grayFace != null;
										}
									});
									// normalise energy of all images
									if (SharedPrefs.useImageEnergyNormlization(getActivity())) {
										FunUtil.map(imagesWithFaces, new Fun<PanshotImage, PanshotImage>() {
											@Override
											public PanshotImage apply(PanshotImage panshotImage) {
												// normalise the face's energy
												// use convolution (kernel = 2D
												// filter) to get image energy
												// (brightness)
												// distribution
												// and normalise face with it
												GenericTuple2<Mat, Mat> normalizedMatEnergy = PanshotUtil.normalizeMatEnergy(
														panshotImage.grayFace, (int) (panshotImage.grayFace.rows() / SharedPrefs
																.getImageEnergyNormalizationSubsamplingFactor(getActivity())),
														(int) (panshotImage.grayFace.cols() / SharedPrefs
																.getImageEnergyNormalizationSubsamplingFactor(getActivity())),
														255.0);
												panshotImage.grayFace = normalizedMatEnergy.value1;
												// DEBUG save energy image for
												// review
												try {
													File f = MediaSaveUtil.getMediaStorageDirectory(getActivity().getResources()
															.getString(R.string.app_media_directory_name));
													MediaSaveUtil.saveMatToJpgFile(new File(f.getAbsolutePath()
															+ "/normalized.jpg"), normalizedMatEnergy.value1);
													MediaSaveUtil.saveMatToJpgFile(new File(f.getAbsolutePath() + "/energy.jpg"),
															normalizedMatEnergy.value2);
												} catch (NotFoundException e) {
													e.printStackTrace();
												} catch (IOException e) {
													e.printStackTrace();
												}
												return panshotImage;
											}
										});
									}
									// RESIZE images as KNN, SVM etc need images
									// that are of same size
									FunUtil.map(imagesWithFaces, new Fun<PanshotImage, PanshotImage>() {
										@Override
										public PanshotImage apply(PanshotImage _t) {
											Imgproc.resize(
													_t.grayFace,
													_t.grayFace,
													new Size(SharedPrefs.getFaceWidth(getActivity()), SharedPrefs
															.getFaceHeight(getActivity())));
											return _t;
										}
									});
									if (imagesWithFaces.size() > 0) {
										// decide which recognition to use
										switch (SharedPrefs.getRecognitionType(getActivity())) {
											case KNN:
												classifyKnn(imagesWithFaces);
												break;

											case SVM:
												classifySvm(imagesWithFaces);
												break;

										}
									} else {
										Toast.makeText(getActivity(),
												getActivity().getResources().getString(R.string.no_faces_detected),
												Toast.LENGTH_LONG).show();
									}

							}
							images.clear();
						}
					}
					break;

				case SwitchToRecognition:
					// make user text invisible
					textviewIdentity.setVisibility(View.INVISIBLE);

					// load training data
					Log.d(TAG, "CameraFragment.update(): loading training panshot images...");
					List<PanshotImage> trainingPanshotImages = loadTrainingData();
					// do image energy normalisation
					if (SharedPrefs.useImageEnergyNormlization(getActivity())) {
						final float subsamplingFactor = SharedPrefs.getImageEnergyNormalizationSubsamplingFactor(getActivity());
						FunUtil.map(trainingPanshotImages, new Fun<PanshotImage, PanshotImage>() {
							@Override
							public PanshotImage apply(PanshotImage panshotImage) {
								// normalise the face's energy
								// use convolution (kernel = 2D filter) to get
								// image energy (brightness) distribution and
								// normalise
								// face with it
								GenericTuple2<Mat, Mat> normalizedMatEnergy = PanshotUtil.normalizeMatEnergy(
										panshotImage.grayFace, (int) (panshotImage.grayFace.rows() / subsamplingFactor),
										(int) (panshotImage.grayFace.cols() / subsamplingFactor), 255.0);
								panshotImage.grayFace = normalizedMatEnergy.value1;
								return panshotImage;
							}
						});
					}
					// separate images to perspectives...
					Map<Integer, TrainingData> trainingdataPerClassifier = new HashMap<Integer, TrainingData>();
					// ... and track amount of images per (subject, perspective)
					Map<GenericTuple2<String, Integer>, Integer> imageAmount = new HashMap<GenericTuple2<String, Integer>, Integer>();
					for (PanshotImage image : trainingPanshotImages) {
						int classifierIndex = getClassifierIndexForAngle(image.angleValues[image.rec.angleIndex]);
						if (!trainingdataPerClassifier.containsKey(classifierIndex)) {
							trainingdataPerClassifier.put(classifierIndex, new TrainingData());
						}
						trainingdataPerClassifier.get(classifierIndex).images.add(image);

						// increase amount of images for this subject and
						// perspective
						// would not look so damn complex if Java had more FP...
						GenericTuple2<String, Integer> subjectPerspectiveKey = new GenericTuple2(image.rec.user.getName(),
								classifierIndex);
						if (!imageAmount.containsKey(subjectPerspectiveKey)) {
							imageAmount.put(subjectPerspectiveKey, 0);
						}
						imageAmount.put(subjectPerspectiveKey, imageAmount.get(subjectPerspectiveKey) + 1);
					}
					for (GenericTuple2<String, Integer> key : imageAmount.keySet()) {
						int amount = imageAmount.get(key);
						if (amount < MIN_AMOUNT_IMAGES_PER_SUBJECT_AND_CLASSIFIER) {
							Toast.makeText(
									getActivity(),
									getActivity().getResources().getString(R.string.too_less_training_data, key.value1,
											"" + amount, "" + key.value2, "" + MIN_AMOUNT_IMAGES_PER_SUBJECT_AND_CLASSIFIER),
									Toast.LENGTH_LONG).show();
							return;
						}
					}

					// RESIZE images as KNN, SVM etc need images that are of
					// same size
					for (TrainingData trainingData : trainingdataPerClassifier.values()) {
						FunUtil.map(trainingData.images, new Fun<PanshotImage, PanshotImage>() {
							@Override
							public PanshotImage apply(PanshotImage _t) {
								Imgproc.resize(_t.grayFace, _t.grayFace, new Size(SharedPrefs.getFaceWidth(getActivity()),
										SharedPrefs.getFaceHeight(getActivity())));
								return _t;
							}
						});
					}

					// PCA
					if (SharedPrefs.usePca(getActivity())) {
						for (TrainingData trainingData : trainingdataPerClassifier.values()) {
							GenericTuple3<Mat, Mat, Mat> pcaComponents = PCAUtil.pcaCompute(trainingData.images);
							trainingData.pcaMean = pcaComponents.value1;
							trainingData.pcaEigenvectors = pcaComponents.value2;
							trainingData.pcaProjections = pcaComponents.value3;
						}
					}

					// we know we have sufficient training data for each
					// classifier
					switch (SharedPrefs.getRecognitionType(getActivity())) {
						case KNN:
							trainKnn(trainingdataPerClassifier);
							// trainJavaCv(trainingdataPerClassifier);
							break;

						case SVM:
							trainSvm(trainingdataPerClassifier);

						default:
							break;
					}

					Log.i(TAG, "CameraFragment.update(): switching to recognition mode done.");
					break;

				case SwitchToTraining:
					// make user text invisible
					textviewIdentity.setVisibility(View.VISIBLE);

				default:
					// Not interested
					break;
			}
		}

		private void trainKnn(Map<Integer, TrainingData> _trainingdataPerClassifier) {
			for (Integer classifierIndex : _trainingdataPerClassifier.keySet()) {
				// ensure classifier exists
				TrainingData trainingData = _trainingdataPerClassifier.get(classifierIndex);
				if (!mKnnClassifiers.containsKey(classifierIndex)) {
					mKnnClassifiers.put(classifierIndex, new KnnClassifier());
				}
				KnnClassifier classifier = mKnnClassifiers.get(classifierIndex);
				// TODO F
				// b) of resulting image: cut out out certain area (ellipse)
				// calculate PCA
				classifier.train(trainingData, SharedPrefs.usePca(getActivity()),
						SharedPrefs.getAmountOfPcaFeatures(getActivity()));
			}
		}

		private void classifyKnn(List<PanshotImage> _images) {
			// classify each image, combine votings
			Map<User, Integer> votings = new HashMap<User, Integer>();
			GenericTuple2<User, Integer> mostVotedUser = null;
			for (PanshotImage image : _images) {
				int classifierIndex = getClassifierIndexForAngle(image.angleValues[image.rec.angleIndex]);
				if (!mKnnClassifiers.containsKey(classifierIndex)) {
					// skip, we cannot classify images we have no classifier for
					continue;
				}
				// classify and remember results
				KnnClassifier classifier = mKnnClassifiers.get(classifierIndex);
				GenericTuple2<User, Map<User, Integer>> res = classifier.classify(image, SharedPrefs.getKnnK(getActivity()),
				// TODO possibly put that to settings
						new DistanceMetric() {
							@Override
							public double distance(double _sample1Feat, double _sample2Feat) {
								return Math.pow(_sample1Feat - _sample2Feat, 2);
								// = euclidean without sqrt. alternative:
								// manhatten distance
								// Math.abs(_sample1Feat - _sample2Feat);
							}
						}, SharedPrefs.usePca(getActivity()), SharedPrefs.getAmountOfPcaFeatures(getActivity()));
				for (User u : res.value2.keySet()) {
					if (!votings.containsKey(u)) {
						votings.put(u, res.value2.get(u));
					} else {
						votings.put(u, votings.get(u) + res.value2.get(u));
					}
					if (mostVotedUser == null || mostVotedUser.value2 < votings.get(u)) {
						mostVotedUser = new GenericTuple2<User, Integer>(u, votings.get(u));
					}
				}
			}
			Log.i(TAG, "votings: " + votings.toString());
			Toast.makeText(
					getActivity(),
					getActivity().getResources().getString(R.string.most_likely_user,
							mostVotedUser.value1.getName() + " (" + mostVotedUser.value2 + " votes)"), Toast.LENGTH_LONG).show();
		}

		private void trainSvm(Map<Integer, TrainingData> _trainingdataPerClassifier) {
			for (Integer classifierIndex : _trainingdataPerClassifier.keySet()) {
				// ensure classifier exists
				TrainingData trainingData = _trainingdataPerClassifier.get(classifierIndex);
				if (!mSvmClassifiers.containsKey(classifierIndex)) {
					mSvmClassifiers.put(classifierIndex, new SvmClassifier());
				}
				SvmClassifier classifier = mSvmClassifiers.get(classifierIndex);
				classifier.train(trainingData, SharedPrefs.usePca(getActivity()),
						SharedPrefs.getAmountOfPcaFeatures(getActivity()));
			}
		}

		private void classifySvm(List<PanshotImage> _images) {
			Log.i("SVM", "start recognizeSvm - image count: " + _images.size());
			// classify each image, combine votings
			Map<User, Double> probabilities = new HashMap<User, Double>();
			GenericTuple2<User, Double> mostVotedUser = null;
			for (PanshotImage image : _images) {
				int classifierIndex = getClassifierIndexForAngle(image.angleValues[image.rec.angleIndex]);
				if (!mSvmClassifiers.containsKey(classifierIndex)) {
					// skip, we cannot classify images we have no classifier for
					Log.i("SVM", "no classifier trained for index: " + classifierIndex);
					continue;
				}
				// get the trained classifier
				SvmClassifier classifier = mSvmClassifiers.get(classifierIndex);
				// classify
				GenericTuple2<User, Map<User, Double>> res = classifier.classify(image, SharedPrefs.usePca(getActivity()),
						SharedPrefs.getAmountOfPcaFeatures(getActivity()));
				// remember probabilities for later images
				for (User u : res.value2.keySet()) {
					if (!probabilities.containsKey(u)) {
						probabilities.put(u, res.value2.get(u));
					} else {
						probabilities.put(u, probabilities.get(u) + res.value2.get(u));
					}
					if (mostVotedUser == null || mostVotedUser.value2 < probabilities.get(u)) {
						mostVotedUser = new GenericTuple2<User, Double>(u, probabilities.get(u));
					}
				}
			}

			Log.i(TAG, "probabilities: " + probabilities.toString());
			Toast.makeText(
					getActivity(),
					getActivity().getResources().getString(R.string.most_likely_user,
							mostVotedUser.value1.getName() + " (" + mostVotedUser.value2 + " votes)"), Toast.LENGTH_LONG).show();
		}

		private void updateUiFromCurrentUser() {
			User user = ((OldMainActivity) getActivity()).mCurrentUser;
			if (user == null) {
				textviewIdentity.setText(getActivity().getResources().getString(R.string.current_user,
						getActivity().getResources().getString(R.string.not_available)));
				return;
			}
			textviewIdentity.setText(getActivity().getResources().getString(R.string.current_user, user.getName()));
		}

		public void onDestroy() {
			Log.i(TAG, "CameraFragment.onDestroy()");
			super.onDestroy();
			((OldMainActivity) getActivity()).mOpenCvCameraView.disableView();
		}

		public void onCameraViewStarted(int width, int height) {
			Log.i(TAG, "CameraFragment.onCameraViewStarted()");
			mGray = new Mat();
			mRgba = new Mat();
		}

		public void onCameraViewStopped() {
			Log.i(TAG, "CameraFragment.onCameraViewStopped()");
			mGray.release();
			mRgba.release();
		}

		public Mat onCameraFrame(CvCameraViewFrame inputFrame) {

			// mRgba = inputFrame.rgba();
			mGray = inputFrame.gray();

			// try to rotate inputFrame for portrait mode
			// Mat mRgbaT = mGray.t();
			// Core.flip(mGray.t(), mRgbaT, 0);
			// Imgproc.resize(mRgbaT, mRgbaT, mGray.size());
			//
			// mGray = mRgbaT;

			// ImageOperationUtil.rotate(mGray, 90);
			// ImageOperationUtil.rotate(mRgba, 90);

			if (mAbsoluteFaceSize == 0) {
				int height = mGray.rows();
				if (Math.round(height * mRelativeFaceSize) > 0) {
					mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
				}
				((OldMainActivity) getActivity()).mNativeDetector_LBPCascadeFrontalface.setMinFaceSize(mAbsoluteFaceSize);
			}

			// copy grayface in case we need it later
			Mat graycopy = null;
			if (mPhotoGyroListener.isNextPhoto()) {
				graycopy = new Mat(mGray.rows(), mGray.cols(), mGray.type());
				mGray.copyTo(graycopy);
			}

			// LIVE FACE DETECTION - NOT THE ONE DONE FOR RECOGNITION!
			Rect[] facesArray = detectFaces(mGray, ((OldMainActivity) getActivity()).mJavaDetector_LBPCascadeFrontalface,
					((OldMainActivity) getActivity()).mNativeDetector_LBPCascadeFrontalface);
			Rect faceBiggest = null;
			for (int i = 0; i < facesArray.length; i++) {
				Core.rectangle(mGray, facesArray[i].tl(), facesArray[i].br(), FACE_RECT_COLOR, 3);
				if (faceBiggest == null || faceBiggest.width < facesArray[i].width) {
					faceBiggest = facesArray[i];
				}
			}

			// time to take next photo
			if (graycopy != null) {
				Mat grayface = null;
				if (faceBiggest != null) {
					grayface = graycopy.submat(faceBiggest);
					// grayface = new Mat(grayface2.rows(), grayface2.cols(),
					// grayface2.type());
					// grayface2.copyTo(grayface);
				}
				long timestamp = System.currentTimeMillis();
				// notify observer about image
				SensorValues sensorValues = SensorComponent.instance().getSensorValues();
				Observation<Float[]> acc = sensorValues.getAccelerationValues();
				Observation<Float[]> rot = sensorValues.getRotationValues();
				Observation<Float> light = sensorValues.getLightValue();
				Log.i(TAG, "taking picture at rotation " + Arrays.toString(rot.value));
				// remember image
				PanshotImage panshotImage = new PanshotImage(graycopy, grayface, sensorValues.getRotationValues().value,
						(acc == null ? null : acc.value), (light == null ? null : light.value), timestamp);
				images.add(panshotImage);
				if (USE_FRONTAL_ONLY) {
					// we've taken 1 image, stop recording automatically right
					// now
					getActivity().runOnUiThread(new Runnable() {
						@Override
						public void run() {
							update(null, FragementUpdateType.ToggleRecording);
						}
					});
				}
			}

			return mGray;
		}

		public void detectFacesInRecordedImagesDependingOnAngle() {
			// determine angle index and angle normalization value
			GenericTuple2<Integer, Float[]> angleIndexNormaliser = PanshotUtil.calculateAngleIndexAndNormalizer(
					images.get(0).angleValues, images.get(images.size() - 1).angleValues);
			int angleIndex = angleIndexNormaliser.value1;
			float angleNormalizer = angleIndexNormaliser.value2[angleIndex];
			for (PanshotImage panshotImage : images) {
				// remember recognition related info
				panshotImage.rec.angleIndex = angleIndex;
				// normalize this image's panshot rotation axis' angle values
				float angle = panshotImage.angleValues[angleIndex] - angleNormalizer;
				// select face detection classifier
				CascadeClassifier cascadeClassifier = ((OldMainActivity) getActivity()).mJavaDetector_LBPCascadeFrontalface;
				DetectionBasedTracker detectionBasedTracker = ((OldMainActivity) getActivity()).mNativeDetector_LBPCascadeFrontalface;
				boolean mirroring = angle < -FRONTAL_MAX_ANGLE;
				boolean usingProfile = Math.abs(angle) > FRONTAL_MAX_ANGLE;
				Mat image = panshotImage.grayImage;
				if (usingProfile) {

					// LBP profile face does not seem to do too good atm, use
					// haarcascades instead
					// cascadeClassifier = ((MainActivity)
					// getActivity()).mJavaDetector_LBPCascadeProfileface;
					// detectionBasedTracker = ((MainActivity)
					// getActivity()).mNativeDetector_LBPCascadeProfileface;

					cascadeClassifier = ((OldMainActivity) getActivity()).mJavaDetector_HaarCascadeProfileface;
					detectionBasedTracker = ((OldMainActivity) getActivity()).mNativeDetector_HaarCascadeProfileface;
					if (mirroring) {
						// flip horizontally
						Mat tmp = new Mat(image.rows(), image.cols(), image.type());
						Core.flip(image, tmp, 1);
						image = tmp;
					}
				}
				Log.d(TAG, "angle is " + angle + ", usingProfile=" + usingProfile + ", mirroring=" + mirroring);
				if (usingProfile || panshotImage.grayFace == null) {
					// we either use profile or don't have a face by now, so
					// detect faces
					Rect[] faces = detectFaces(image, cascadeClassifier, detectionBasedTracker);
					Log.d(TAG, "we found " + faces.length + " faces.");
					// determine biggest face and extract facial region
					Rect faceBiggest = null;
					for (Rect r : faces) {
						if (faceBiggest == null || faceBiggest.width < r.width) {
							faceBiggest = r;
						}
					}
					if (faceBiggest != null) {
						panshotImage.grayFace = panshotImage.grayImage.submat(faceBiggest);
						if (mirroring) {
							// flip horizontally
							Mat tmp = new Mat(panshotImage.grayFace.rows(), panshotImage.grayFace.cols(),
									panshotImage.grayFace.type());
							Core.flip(panshotImage.grayFace, tmp, 1);
							panshotImage.grayFace = tmp;
						}
					}
				} else {
					Log.d(TAG, "image already contained a detected face, not doing face detection again.");
				}
			}
		}

		private Rect[] detectFaces(Mat image, CascadeClassifier cascadeClassifier, DetectionBasedTracker detectionBasedTracker) {
			MatOfRect faces = new MatOfRect();
			if (((OldMainActivity) getActivity()).mDetectorType == JAVA_DETECTOR) {
				if (cascadeClassifier != null)
					cascadeClassifier.detectMultiScale(image, faces, 1.1, 2, 2, // opencvtodo:
																				// objdetect.CV_HAAR_SCALE_IMAGE
							new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
			} else if (((OldMainActivity) getActivity()).mDetectorType == NATIVE_DETECTOR) {
				if (detectionBasedTracker != null)
					detectionBasedTracker.detect(image, faces);
			} else {
				Log.e(TAG, "Detection method is not selected!");
			}
			return faces.toArray();
		}

		/**
		 * @return the index of the classifier in {@link #mSvmClassifiers} which
		 *         corresponds to the angle.
		 */
		private int getClassifierIndexForAngle(float normalizedAngle) {
			return (int) (normalizedAngle / ANGLE_DIFF_OF_PHOTOS);
		}

		private void setMinFaceSize(float faceSize) {
			mRelativeFaceSize = faceSize;
			mAbsoluteFaceSize = 0;
		}

		private void setDetectorType(int type) {
			if (((OldMainActivity) getActivity()).mDetectorType != type) {
				((OldMainActivity) getActivity()).mDetectorType = type;

				if (type == NATIVE_DETECTOR) {
					Log.i(TAG, "Detection Based Tracker enabled");
					((OldMainActivity) getActivity()).mNativeDetector_LBPCascadeFrontalface.start();
				} else {
					Log.i(TAG, "Cascade detector enabled");
					((OldMainActivity) getActivity()).mNativeDetector_LBPCascadeFrontalface.stop();
				}
			}
		}
	}
}
