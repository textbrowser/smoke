package org.purple.smoke;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button button = (Button) findViewById(R.id.clear_chat);

        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final TextView textView = (TextView) findViewById(R.id.chat_messages);

                textView.setText("");
                }
            });
    }
}
