package usc.edu.crowdtasker;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;

import usc.edu.crowdtasker.data.model.User;
import usc.edu.crowdtasker.data.provider.UserProvider;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

public class ProfileActivity extends Activity {
	
	public static final String TAG = "ProfileActivity";
	
	public static final int SELECT_PICTURE = 1;
	public static final double PROFILE_PIC_DIM = 180.0;
	private User currentUser;
	
	private TextView username;
	private EditText firstName;
	private EditText lastName;
	private EditText email;
	private RatingBar ratingBar;
	private Button saveBtn;
	private ImageView profilePicture;
	
	private ProgressDialog progressDialog;
	private Bitmap currentProfilePic;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.profile_activity);
		currentUser = UserProvider.getCurrentUser(getApplicationContext());
		
		username = (TextView)findViewById(R.id.username);
		firstName = (EditText)findViewById(R.id.first_name);
		lastName = (EditText)findViewById(R.id.last_name);
		email = (EditText)findViewById(R.id.email);
		saveBtn = (Button)findViewById(R.id.save_btn);
		ratingBar = (RatingBar)findViewById(R.id.rating_bar);
		profilePicture = (ImageView) findViewById(R.id.profile_picture);
		ratingBar.setActivated(false);
		
		progressDialog = new ProgressDialog(this);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        
		if(currentUser != null){
			username.setText(currentUser.getLogin());
	        progressDialog.setMessage(getString(R.string.loading_profile));
	        progressDialog.show();
			new AsyncTask<Long, Void, User>() {

				@Override
				protected User doInBackground(Long... params) {
					User user = UserProvider.getUserById(params[0]);
					currentProfilePic = UserProvider.getProfilePic(user);
					return user;
				}
				
				@Override
				protected void onPostExecute(User result) {
					progressDialog.dismiss();
					if(result == null)
						return;
					currentUser = result;
					if(result.getEmail() != null)
						email.setText(result.getEmail());
					if(result.getFirstName() != null)
						firstName.setText(result.getFirstName());
					if(result.getLastName() != null)
						lastName.setText(result.getLastName());
					if(result.getRating() != null)
						ratingBar.setRating(result.getRating().floatValue());
					if(currentProfilePic != null)
						setProfilePic(currentProfilePic);
				}
			}.execute(currentUser.getId());
			
			profilePicture.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
					photoPickerIntent.setType("image/*");
					startActivityForResult(photoPickerIntent, SELECT_PICTURE);    
				}
			});
			
			saveBtn.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					currentUser.setFirstName(firstName.getText().toString());
					currentUser.setLastName(lastName.getText().toString());
					currentUser.setEmail(email.getText().toString());
			        progressDialog.setMessage(getString(R.string.saving_profile));

					new AsyncTask<User, Void, Boolean>(){
						protected void onPreExecute() {
							progressDialog.show();
						}
						@Override
						protected Boolean doInBackground(User... params) {
							return UserProvider.updateUser(params[0]);
						}
						
						protected void onPostExecute(Boolean result) {
							if(result)
								Toast.makeText(getApplicationContext(),
										R.string.profile_update_success, Toast.LENGTH_LONG).show();
							else Toast.makeText(getApplicationContext(),
										R.string.profile_update_error, Toast.LENGTH_LONG).show();
							progressDialog.dismiss();
						}
						
					}.execute(currentUser);
				}
			});
		}
		
	}
	
	private Bitmap setProfilePic(Bitmap pic){
		if(pic == null)
			return null;
		 double max = Math.max(pic.getHeight(), pic.getWidth());
         double factor = profilePicture.getWidth() / max ;
         Bitmap scaledBmp = Bitmap.createScaledBitmap(pic, 
         		(int)(factor * pic.getWidth()), 
         		(int)(factor * pic.getHeight()), false);
         profilePicture.setImageBitmap(scaledBmp);
         
         return scaledBmp;
	}
	protected void onActivityResult(int requestCode, int resultCode, 
		       Intent imageReturnedIntent) {
		    super.onActivityResult(requestCode, resultCode, imageReturnedIntent); 

		    switch(requestCode) { 
		    case SELECT_PICTURE:
		        if(resultCode == RESULT_OK){  
		        
		        	ParcelFileDescriptor parcelFD = null;
		        	try {
			            Uri selectedUri = imageReturnedIntent.getData();
		        		parcelFD = getContentResolver().openFileDescriptor(selectedUri, "r");
		        	    FileDescriptor imageSource = parcelFD.getFileDescriptor();

		        	    BitmapFactory.Options o = new BitmapFactory.Options();
	        	        Bitmap selectedBmp = BitmapFactory.decodeFileDescriptor(imageSource, null, o);
	        	        
			            currentProfilePic = setProfilePic(selectedBmp);
			            if(currentProfilePic == null)
			            	Toast.makeText(getApplicationContext(),
			            			R.string.profile_pic_error, Toast.LENGTH_LONG).show();
			            
			            new AsyncTask<Bitmap, Void, Boolean>(){

							@Override
							protected Boolean doInBackground(Bitmap... params) {
								return UserProvider.uploadProfilePicture(currentUser, params[0]);
							}
			            	
			            }.execute(currentProfilePic);
		        	        
	        	    } catch (FileNotFoundException e) {
	        	    	Log.e(TAG, e.getMessage());
	        	    } finally {
	        	        if (parcelFD != null){
	        	            try {
	        	                parcelFD.close();
	        	            } catch (IOException e) {
	        	            	Log.e(TAG, e.getMessage());
	        	            }
	        	        }
	        	    }
		        }
		        
		    }
		}

}
