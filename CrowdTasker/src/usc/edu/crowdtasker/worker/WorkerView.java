package usc.edu.crowdtasker.worker;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

import usc.edu.crowdtasker.R;
import usc.edu.crowdtasker.UpdatableFragment;
import usc.edu.crowdtasker.data.model.Task;
import usc.edu.crowdtasker.data.model.User;
import usc.edu.crowdtasker.data.provider.TaskProvider;
import usc.edu.crowdtasker.data.provider.UserProvider;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import usc.edu.crowdtasker.data.provider.RouteProvider;
import usc.edu.crowdtasker.worker.TaskCompletionDialogFragment.OnFinishListener;

public class WorkerView extends Fragment implements LocationListener,
		OnMarkerClickListener, OnMapClickListener, UpdatableFragment,
		OnClickListener, OnFinishListener {

	public static final String TAG = "WorkerView";

	private GoogleMap mMap;
	private RelativeLayout mapWrapper;
	private View rootView;
	private LocationManager locationManager;

	private static final long MIN_TIME = 0;
	private static final float MIN_DISTANCE = 0;

	private boolean locationInitialized = false;
	private boolean isOnTask = false;

	private HashMap<Marker, Task> taskMarkerMapping;
	private Marker openPickupMarker;
	private Marker openDropoffMarker;

	private Marker currentLocationMarker;

	private Location currentLocation;
	private Polyline currentRoutePoly;

	private SharedPreferences prefs;
	private User currentUser;
	private Task currentTask;

	private DateFormat dateFormat;
	private NumberFormat moneyFormat;

	private Button acceptBtn;
	private Button declineBtn;

	private ImageView taskerPicture;
	private Bitmap currentTaskerPicture;
	private TextView timerTextView;
	private CountDownTimer timer;
	private Time currentTime;

	public static final int UPDATE_INTERVAL = 10000; // Update interval in
														// milliseconds
	private Timer updateTimer;

	/**
	 * Returns a new instance of this fragment for the given section number.
	 */
	public static WorkerView newInstance() {
		WorkerView fragment = new WorkerView();
		Bundle args = new Bundle();
		fragment.setArguments(args);
		return fragment;
	}

	public WorkerView() {
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View rootView = inflater
				.inflate(R.layout.worker_view, container, false);
		this.rootView = rootView;
		mapWrapper = (RelativeLayout) rootView.findViewById(R.id.map_wrapper);
		mapWrapper.setVisibility(View.INVISIBLE);
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		setUpMapIfNeeded();

		acceptBtn = (Button) rootView.findViewById(R.id.accpetTaskBtn);
		declineBtn = (Button) rootView.findViewById(R.id.declineTaskBtn);
		taskerPicture = (ImageView) rootView.findViewById(R.id.tasker_picture);

		dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
				DateFormat.SHORT);
		moneyFormat = NumberFormat.getCurrencyInstance();

		timerTextView = (TextView) rootView.findViewById(R.id.timerTextView);

		return rootView;
	}

	@Override
	public void onDestroyView() {
		locationManager.removeUpdates(this);
		if (updateTimer != null) {
			updateTimer.cancel();
			updateTimer.purge();
		}
		super.onDestroyView();
	}

	/**
	 * Sets up the map if it is possible to do so (i.e., the Google Play
	 * services APK is correctly installed) and the map has not already been
	 * instantiated.. This will ensure that we only ever call
	 * {@link #setUpMap()} once when {@link #mMap} is not null.
	 * <p>
	 * If it isn't installed {@link SupportMapFragment} (and
	 * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt
	 * for the user to install/update the Google Play services APK on their
	 * device.
	 * <p>
	 * A user can return to this FragmentActivity after following the prompt and
	 * correctly installing/updating/enabling the Google Play services. Since
	 * the FragmentActivity may not have been completely destroyed during this
	 * process (it is likely that it would only be stopped or paused),
	 * {@link #onCreate(Bundle)} may not be called again so we should call this
	 * method in {@link #onResume()} to guarantee that it will be called.
	 */
	private void setUpMapIfNeeded() {
		// Do a null check to confirm that we have not already instantiated the
		// map.
		if (mMap == null) {
			// Try to obtain the map from the SupportMapFragment.
			mMap = ((SupportMapFragment) getFragmentManager().findFragmentById(
					R.id.map)).getMap();

			// Check if we were successful in obtaining the map.
			if (mMap != null) {
				setUpMap();
			}
		}
	}

	/**
	 * This is where we can add markers or lines, add listeners or move the
	 * camera. In this case, we just add a marker near Africa. This should only
	 * be called once and when we are sure that {@link #mMap} is not null.
	 */
	private void setUpMap() {
		mMap.setMyLocationEnabled(true);
		mMap.setOnMapClickListener(this);
		mMap.setOnMarkerClickListener(this);
		mMap.getUiSettings().setZoomControlsEnabled(false);
		locationManager = (LocationManager) getActivity().getSystemService(
				Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(
				LocationManager.NETWORK_PROVIDER, MIN_TIME, MIN_DISTANCE, this);
	}

	private void showTasksOnMap(Location currentLocation) {
		if (currentLocation == null)
			return;
		final double rangeRadius = (double) prefs.getInt(
				getString(R.string.pref_range_radius), 10);
		final String rangeUnit = prefs.getString(
				getString(R.string.pref_range_unit), "mile");
		final int numNearestTasks = prefs.getInt(
				getString(R.string.pref_nearest_tasks), 10);

		new AsyncTask<Location, Void, List<Task>>() {

			@Override
			protected List<Task> doInBackground(Location... params) {
				Location location = params[0];
				double[] latLng = new double[] { location.getLatitude(),
						location.getLongitude() };
				List<Task> tasks = TaskProvider.getTasksRange(latLng,
						rangeRadius, rangeUnit, numNearestTasks);
				return tasks;
			}

			@Override
			protected void onPostExecute(List<Task> tasks) {
				taskMarkerMapping = new HashMap<Marker, Task>();
				mMap.clear();
				for (Task task : tasks) {
					MarkerOptions opt = new MarkerOptions();
					LatLng pickupLoc = new LatLng(task.getPickupLocation()[0],
							task.getPickupLocation()[1]);
					opt.position(pickupLoc);
					switch (task.getStatus()) {

					case CREATED:
					case CANCELED:
						if (task.getOwnerId() == currentUser.getId()) {
							opt.icon(BitmapDescriptorFactory
									.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW));
						} else {
							opt.icon(BitmapDescriptorFactory
									.defaultMarker(BitmapDescriptorFactory.HUE_RED));
						}
						break;

					case ACCEPTED:
						if (task.getWorkerId() == currentUser.getId()) {
							currentTask = task;							
							String pickUp = task.getPickupAddress();
							String dropOff = task.getDropoffAddress();

							new AsyncTask<String, Void, List<LatLng>>() {
								@Override
								protected List<LatLng> doInBackground(String... params) {
									return RouteProvider.getRoute(params[0], params[1]);
								}

								@Override
								protected void onPostExecute(List<LatLng> result) {
									showRoute(result);
								}
							}.execute(pickUp, dropOff);
							
							showTaskDetailsPanel(task);
							onClick(acceptBtn);
							opt.icon(BitmapDescriptorFactory
									.defaultMarker(BitmapDescriptorFactory.HUE_RED));
							mMap.addMarker(opt);
							
							opt = new MarkerOptions();
							pickupLoc = new LatLng(task.getDropoffLocation()[0],
									task.getDropoffLocation()[1]);
							opt.position(pickupLoc);
							opt.icon(BitmapDescriptorFactory
									.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
							mMap.addMarker(opt);
							return;
						}
						opt.icon(BitmapDescriptorFactory
								.defaultMarker(BitmapDescriptorFactory.HUE_RED));
						break;
					case COMPLETED:
						break;
					}

					Marker marker = mMap.addMarker(opt);
					taskMarkerMapping.put(marker, task);
				}
			}
		}.execute(currentLocation);
	}

	@Override
	public void onLocationChanged(Location location) {
		currentLocation = location;

		if (isOnTask) {
			double dropOffLat = currentTask.getDropoffLocation()[0];
			double dropOffLon = currentTask.getDropoffLocation()[1];

			double currentLat = location.getLatitude();
			double currentLon = location.getLongitude();

			// Check differnece between latllong of dest at 4th decimal place
			// the 4th decimal place is accurate upto 11m

			double currentLoc[] = new double[] { currentLat, currentLon };
			double dropOffLoc[] = new double[] { dropOffLat, dropOffLon };

			if (isCloseTo(currentLoc, dropOffLoc)) {
				Log.d(TAG, "Approaching destination");
				showTaskCompletionDialog();
			}
			Log.d(TAG, "cur =" + currentLoc[0] + "," + currentLoc[1]
					+ ";;;, des =" + dropOffLoc[0] + "," + dropOffLoc[1]);
		}

		if (!locationInitialized) {
			LatLng latLng = new LatLng(currentLocation.getLatitude(),
					currentLocation.getLongitude());
			CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(
					latLng, 15);
			mMap.moveCamera(cameraUpdate);// animateCamera(cameraUpdate);
			mapWrapper.setVisibility(View.VISIBLE);
			locationInitialized = true;
			update();
		}
	}

	private boolean showingTaskCompletionDialog = false;

	private void showTaskCompletionDialog() {
		if (showingTaskCompletionDialog) {
			return;
		} else {
			FragmentManager fragmentManager = getFragmentManager();
			TaskCompletionDialogFragment TaskCompletionDialog = new TaskCompletionDialogFragment(
					currentTask, this);
			TaskCompletionDialog.setCancelable(true);
			TaskCompletionDialog.setDialogTitle("Approaching Destination");
			TaskCompletionDialog.show(fragmentManager, "Yes/No Dialog");
			showingTaskCompletionDialog = true;
		}

	}

	public void onFinish(boolean completionResult) {
		if (completionResult) {
			currentTask.setStatus(Task.TaskStatus.COMPLETED);
		} else {
			// TODO : Handle task not comepleted state
			// possible reaon for task incomepletion maybe
			// destination not present, destination not opened door
			// etc
			currentTask.setStatus(Task.TaskStatus.CANCELED);
			// TODO:Display form for greivance or why task want not completed
		}
		new AsyncTask<Task, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Task... params) {
				return TaskProvider.updateTask(params[0]);
			}

			@Override
			protected void onPostExecute(Boolean p) {
				update();
			}

		}.execute(currentTask);

		// update view
		isOnTask = false;
		rootView.findViewById(R.id.accpetTaskBtn).setVisibility(View.VISIBLE);
		rootView.findViewById(R.id.declineTaskBtn).setVisibility(View.GONE);
		timer.cancel();
		String res = completionResult ? "Completed" : "Cancelled";

		Toast.makeText(getActivity(), "Task " + res, Toast.LENGTH_LONG).show();
		;
		timerTextView.setText("");
		timerTextView.setVisibility(View.GONE);
		taskerPicture.setVisibility(View.VISIBLE);
	}

	private boolean isCloseTo(double[] currentLoc, double[] dropOffLoc) {
		double diffLat = currentLoc[0] - dropOffLoc[0];
		double diffLon = currentLoc[1] - dropOffLoc[1];
		if ((Math.abs(diffLat) < 0.001) && (Math.abs(diffLon) < 0.001)) {
			return true;
		}
		return false;
	}

	@Override
	public boolean onMarkerClick(Marker marker) {

		if (isOnTask) {
			return true;
		}
		if (openDropoffMarker != null) {
			if (openDropoffMarker.equals(marker)) {
				if (openPickupMarker != null)
					openPickupMarker.showInfoWindow();
				return true;
			}
			openDropoffMarker.remove();
			openDropoffMarker = null;
		}

		if (taskMarkerMapping != null && taskMarkerMapping.containsKey(marker)) {
			Task task = taskMarkerMapping.get(marker);
			if (task.getDropoffLocation() != null) {
				MarkerOptions opt = new MarkerOptions();
				LatLng dropoffLoc = new LatLng(task.getDropoffLocation()[0],
						task.getDropoffLocation()[1]);
				opt.position(dropoffLoc);
				opt.title(getString(R.string.dropoff) + ": " + task.getName());
				opt.icon(BitmapDescriptorFactory
						.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));

				openDropoffMarker = mMap.addMarker(opt);

				// Show route
				if (currentRoutePoly != null) {
					currentRoutePoly.remove();
				}
				String pickUp = task.getPickupAddress();
				String dropOff = task.getDropoffAddress();

				new AsyncTask<String, Void, List<LatLng>>() {
					@Override
					protected List<LatLng> doInBackground(String... params) {
						return RouteProvider.getRoute(params[0], params[1]);
					}

					@Override
					protected void onPostExecute(List<LatLng> result) {
						showRoute(result);
					}
				}.execute(pickUp, dropOff);
				openDropoffMarker.showInfoWindow();
				showTaskDetailsPanel(task);
			}
			openPickupMarker = marker;
			openPickupMarker.showInfoWindow();

			fitToOpenMarkers();
		}
		return true;
	}

	private void showTaskDetailsPanel(Task task) {
		if (task.getOwnerId() == currentUser.getId()) {
			Toast.makeText(getActivity(),
					"Cannot accept your own tasks! Select some other task",
					Toast.LENGTH_SHORT).show();
			final RelativeLayout taskPanel = (RelativeLayout) rootView
					.findViewById(R.id.task_details_panel);
			taskPanel.setVisibility(View.GONE);
			return;
		}
		
		/*if (task.getStatus() == Task.TaskStatus.COMPLETED) {
			Toast.makeText(getActivity(),
					"Task already comeplted. Select some other task",
					Toast.LENGTH_SHORT).show();
			final RelativeLayout taskPanel = (RelativeLayout) rootView
					.findViewById(R.id.task_details_panel);
			taskPanel.setVisibility(View.GONE);
			return;
		}*/
		
		currentTask = task;
		final RelativeLayout taskPanel = (RelativeLayout) rootView
				.findViewById(R.id.task_details_panel);
		taskPanel.setVisibility(View.VISIBLE);

		TextView taskName = (TextView) mapWrapper.findViewById(R.id.taskName);
		TextView taskDescription = (TextView) mapWrapper
				.findViewById(R.id.taskDescription);
		TextView deadline = (TextView) rootView.findViewById(R.id.deadline);
		TextView payment = (TextView) rootView.findViewById(R.id.payment);
		final TextView tasker = (TextView) rootView.findViewById(R.id.tasker);

		final RatingBar taskerRating = (RatingBar) rootView
				.findViewById(R.id.tasker_rating_bar);

		if (currentTask.getName() != null)
			taskName.setText(currentTask.getName());

		if (currentTask.getDescription() != null)
			taskDescription.setText(currentTask.getDescription());

		if (currentTask.getDeadline() != null)
			deadline.setText(dateFormat.format(currentTask.getDeadline()));

		if (currentTask.getPayment() != null)
			payment.setText(moneyFormat.format(currentTask.getPayment()));

		if (currentTask.getOwnerId() != null) {
			new AsyncTask<Long, Void, User>() {

				@Override
				protected User doInBackground(Long... params) {
					User user = UserProvider.getUserById(params[0]);
					currentTaskerPicture = UserProvider.getProfilePic(user);
					return user;
				}

				@Override
				protected void onPostExecute(User result) {
					if (result != null) {

						setTaskerPicture(currentTaskerPicture);
						tasker.setText(result.getLogin());
						if (result.getRating() != null)
							taskerRating.setRating(result.getRating()
									.floatValue());
					} else {
						tasker.setText(R.string.not_assigned);
					}
				}

			}.execute(currentTask.getOwnerId());
		} else
			tasker.setText(R.string.not_assigned);

		acceptBtn.setOnClickListener(this);
		declineBtn.setOnClickListener(this);

	}

	private Bitmap setTaskerPicture(Bitmap pic) {
		if (pic == null)
			return null;
		Log.d(TAG, pic.getWidth() + " " + pic.getHeight());
		double max = Math.max(pic.getHeight(), pic.getWidth());
		double factor = taskerPicture.getWidth() / max;

		if (factor == 0) {
			return null;
		}
		Bitmap scaledBmp = Bitmap.createScaledBitmap(pic,
				(int) (factor * pic.getWidth()),
				(int) (factor * pic.getHeight()), false);
		taskerPicture.setImageBitmap(scaledBmp);
		return scaledBmp;
	}

	private void showRoute(List<LatLng> routePoints) {
		if (currentRoutePoly != null)
			currentRoutePoly.remove();

		PolylineOptions options = new PolylineOptions().width(5)
				.color(Color.BLUE).geodesic(true);
		for (int z = 0; z < routePoints.size(); z++) {
			LatLng point = routePoints.get(z);
			options.add(point);
		}
		// POLYLINES = options;
		currentRoutePoly = mMap.addPolyline(options);

	}

	private void fitToOpenMarkers() {
		if (openPickupMarker == null)
			return;
		CameraUpdate cameraUpdate;

		if (openDropoffMarker == null) {
			cameraUpdate = CameraUpdateFactory.newLatLngZoom(
					openPickupMarker.getPosition(), 15);

		} else {
			LatLngBounds.Builder boundsBuilder = LatLngBounds.builder();
			boundsBuilder.include(openPickupMarker.getPosition());
			boundsBuilder.include(openDropoffMarker.getPosition());

			LatLngBounds bounds = boundsBuilder.build();
			cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 150);
		}

		mMap.animateCamera(cameraUpdate);
	}

	@Override
	public void onMapClick(LatLng latLng) {

		// if worker is performing a task
		// do nothing
		if (isOnTask) {
			// do nothing

		} else {
			// hide task details panel
			mapWrapper.findViewById(R.id.task_details_panel).setVisibility(
					View.GONE);
			if (currentRoutePoly != null) {
				currentRoutePoly.remove();
			}
			if (openDropoffMarker != null) {
				openDropoffMarker.remove();
				openDropoffMarker = null;
			}
		}
	}

	@Override
	public void onClick(View v) {

		if (v.getId() == declineBtn.getId()) {
			isOnTask = false;
			acceptBtn.setVisibility(View.VISIBLE);
			declineBtn.setVisibility(View.GONE);
			updateViewForTaskAbort();
		}

		else if (v.getId() == acceptBtn.getId()) {
			isOnTask = true;
			showingTaskCompletionDialog = false;
			acceptBtn.setVisibility(View.GONE);
			declineBtn.setVisibility(View.VISIBLE);
			updateViewForTaskAccept();
		}
	}

	private void updateViewForTaskAbort() {
		// TODO: display dialogbox to abort task
		currentTask.setStatus(Task.TaskStatus.CANCELED);
		Log.d(TAG, "cancelled the task");

		new AsyncTask<Task, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Task... params) {
				return TaskProvider.updateTask(params[0]);
			}

			@Override
			protected void onPostExecute(Boolean c) {
				Toast.makeText(getActivity(), "Task Aborted",
						Toast.LENGTH_SHORT).show();
			}

		}.execute(currentTask);

		timer.cancel();
		timerTextView.setVisibility(View.GONE);
		taskerPicture.setVisibility(View.VISIBLE);
		Log.d(TAG + "; Abort pressed", currentTask.toJSON().toString());
	}

	private void updateViewForTaskAccept() {

		// Show timer
		showingTaskCompletionDialog = false;
		currentTime = new Time();
		currentTime.setToNow();
		final long dl = currentTask.getDeadline().getTime();
		final long ct = currentTime.toMillis(true);

		Log.d(TAG + " TIMER ", "" + (ct - dl));

		timerTextView = (TextView) rootView.findViewById(R.id.timerTextView);
		taskerPicture.setVisibility(View.GONE);
		timerTextView.setVisibility(View.VISIBLE);
		timer = new CountDownTimer(dl - ct, 1000) {

			public void onTick(long millisUntilFinished) {
				String text = dateFormat.format((millisUntilFinished / 1000));

				long seconds = millisUntilFinished / 1000;
				long minutes = seconds / 60;
				long hours = minutes / 60;
				long days = hours / 24;

				text = String
						.format("%02d:%02d:%02d:%02d",
								TimeUnit.MILLISECONDS
										.toDays(millisUntilFinished),
								TimeUnit.MILLISECONDS
										.toHours(millisUntilFinished)
										- TimeUnit.DAYS.toHours(TimeUnit.MILLISECONDS
												.toDays(millisUntilFinished)),
								TimeUnit.MILLISECONDS
										.toMinutes(millisUntilFinished)
										- TimeUnit.HOURS
												.toMinutes(TimeUnit.MILLISECONDS
														.toHours(millisUntilFinished)),
								TimeUnit.MILLISECONDS
										.toSeconds(millisUntilFinished)
										- TimeUnit.MINUTES
												.toSeconds(TimeUnit.MILLISECONDS
														.toMinutes(millisUntilFinished)));
				timerTextView.setText(text);
			}

			public void onFinish() {
				timerTextView.setText("done!");
			}
		}.start();

		currentTask.setStatus(Task.TaskStatus.ACCEPTED);
		currentTask.setWorkerId(currentUser.getId());

		currentTask
				.setWorkerLocation(new double[] {
						currentLocation.getLatitude(),
						currentLocation.getLongitude() });
		Log.d(TAG, "accpeted a task");

		new AsyncTask<Task, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Task... params) {
				return TaskProvider.updateTask(params[0]);
			}

		}.execute(currentTask);
	}

	@Override
	public void onProviderDisabled(String provider) {
	}

	@Override
	public void onProviderEnabled(String provider) {
	}

	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
	}

	@Override
	public void update() {
		currentUser = UserProvider.getCurrentUser(getActivity());
		showTasksOnMap(currentLocation);

	}

	@Override
	public void stopUpdate() {
		if (updateTimer != null) {
			updateTimer.cancel();
			updateTimer.purge();
		}
	}

}
