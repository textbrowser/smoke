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

        final Button button1 = (Button) findViewById(R.id.clear_chat);

        button1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final TextView textView = (TextView) findViewById(R.id.chat_messages);

                textView.setText("");
                }
            });

        final Button button2 = (Button) findViewById(R.id.send_chat_message);

        button2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final TextView textView1 = (TextView) findViewById(R.id.chat_messages);
                final TextView textView2 = (TextView) findViewById(R.id.chat_send_text);

                textView1.append("me: " + textView2.getText() + "\n");
                textView2.setText("");
            }
        });
    }
}
