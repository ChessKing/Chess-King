package org.petero.droidfish.activities;

import org.petero.droidfish.R;
import org.petero.droidfish.gamelogic.GameTree.Node;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/** Activity to edit PGN comments. */
public class EditComments extends Activity {
	TextView preComment, postComment, nag;
	private Button okButton;
	private Button cancelButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        setContentView(R.layout.edit_comments);
        preComment = (TextView)findViewById(R.id.ed_comments_pre);
        TextView moveView = (TextView)findViewById(R.id.ed_comments_move);
        nag = (TextView)findViewById(R.id.ed_comments_nag);
        postComment = (TextView)findViewById(R.id.ed_comments_post);
        okButton = (Button)findViewById(R.id.ed_comments_ok);
        cancelButton = (Button)findViewById(R.id.ed_comments_cancel);

		postComment.requestFocus();

		Intent data = getIntent();
		Bundle bundle = data.getBundleExtra("org.petero.droidfish.comments");
		String pre = bundle.getString("preComment");
		String post = bundle.getString("postComment");
		String move = bundle.getString("move");
		int nagVal = bundle.getInt("nag");
		preComment.setText(pre);
		postComment.setText(post);
		moveView.setText(move);
		String nagStr = Node.nagStr(nagVal).trim();
		if ((nagStr.length() == 0) && (nagVal > 0))
			nagStr = String.format("%d", nagVal);
		nag.setText(nagStr);

		okButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				sendBackResult();
			}
		});
		cancelButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
	}
	
	private final void sendBackResult() {
		String pre = preComment.getText().toString().trim();
		String post = postComment.getText().toString().trim();
		int nagVal = Node.strToNag(nag.getText().toString());
		
		Bundle bundle = new Bundle();
		bundle.putString("preComment", pre);
		bundle.putString("postComment", post);
		bundle.putInt("nag", nagVal);
		Intent data = new Intent();
		data.putExtra("org.petero.droidfish.comments", bundle);
		setResult(RESULT_OK, data);
		finish();
	}
}
