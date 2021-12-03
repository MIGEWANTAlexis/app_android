package cpe.alexis.projet;

import androidx.appcompat.app.AppCompatActivity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    /*
     * view variables
     */
    private Button button_temperature_luminosity;
    private Button button_luminosity_temperature;
    private Button button_connect;
    private EditText input_ip;
    private EditText input_port;
    private TextView text_message;

    /*
     * Application variables (for its operation)
     */
    private InetAddress address;
    private Integer port;
    private DatagramSocket udp_socket;
    private String display_direction_configuration;
    private ReceiverTask receiverTask;
    private Encryption encryption = new Encryption(3);
    private static final String IPV4_PATTERN = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$"; // MODIF A TESTER 2
    private static final String PORT_PATTERN = "^[0-9]*$"; // MODIF A TESTER 3

    /************* application functions *************/

    /*
     * initialize udp socket
     */
    private void set_udp_socket() {
        try {
            udp_socket = new DatagramSocket();
        } catch (PortUnreachableException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * takes a message as parameter and sends it in udp to the initiated address
     * @param message string message to send
     */
    private void send_udp_packet(String message) {
        (new Thread() {
            @Override
            public void run() {
                byte[] datas = message.getBytes();
                DatagramPacket packet = new DatagramPacket(datas, datas.length, address, port);
                try {
                    udp_socket.send(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void get_values() {
        String encrypted_message = encryption.encrypt("getValues()"); // MODIF A TESTER 1
        send_udp_packet(encrypted_message);
    }

    private boolean is_ipv4_valid(String ipv4) { // MODIF A TESTER 3
        Pattern pattern = Pattern.compile(IPV4_PATTERN);
        Matcher matcher = pattern.matcher(ipv4);
        return matcher.matches();
    }

    private boolean is_port_valid(String port) { // MODIF A TESTER 3
        Pattern pattern = Pattern.compile(PORT_PATTERN);
        Matcher matcher = pattern.matcher(port);
        return matcher.matches();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /*
         * get the components of the view to assign the variables
         */
        button_temperature_luminosity = (Button) findViewById(R.id.button_temperature_luminosity);
        button_luminosity_temperature = (Button) findViewById(R.id.button_luminosity_temperature);
        button_connect = (Button) findViewById(R.id.button_connect);
        input_ip = (EditText) findViewById(R.id.input_ip);
        input_port = (EditText) findViewById(R.id.input_port);
        text_message = (TextView) findViewById(R.id.text_message);

        /*
         * initialize an udp socket and run udp message reception in background
         */
        set_udp_socket();
        receiverTask = (ReceiverTask) (new ReceiverTask()).execute();


        button_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    String input_text_address = String.valueOf(input_ip.getText());
                    String input_text_port = String.valueOf(input_port.getText());
                    if(is_ipv4_valid(input_text_address) && is_port_valid(input_text_port)) {
                        address = InetAddress.getByName(input_text_address);
                        port = Integer.parseInt(input_text_port);
                        button_temperature_luminosity.setEnabled(true);
                        button_luminosity_temperature.setEnabled(true);
                        text_message.setText("Connection success!");
                    } else {
                        text_message.setText("PLEASE CHECK YOUR IP AND PORT");
                    }
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        });

        button_temperature_luminosity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                display_direction_configuration = "TL";
                get_values(); // MODIF A TESTER 1
                send_udp_packet(encryption.encrypt(display_direction_configuration));
            }
        });

        button_luminosity_temperature.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                display_direction_configuration = "LT";
                get_values(); // MODIF A TESTER 1
                send_udp_packet(encryption.encrypt(display_direction_configuration));
            }
        });
    }

    /*
     * subclass that runs in the background and detects udp packets (reception)
     */
    private class ReceiverTask extends AsyncTask<Void, byte[], Void> {

        /*
         * background for the reception
         */
        protected Void doInBackground(Void... nothing) {
            while (true) {
                byte[] datas = new byte[1024];
                DatagramPacket packet = new DatagramPacket(datas, datas.length);

                try {
                    if(udp_socket != null) {
                        udp_socket.receive(packet);
                        String udp_message_received = new String(packet.getData(), 0, packet.getLength());
                        String decrypted_message = encryption.decrypt(udp_message_received); // MODIF A TESTER 1
                        String[] splited_message = decrypted_message.split(",");
                        String[] temperatures_luminosities = {splited_message[0].split("l")[1], splited_message[1].split("t")[1]};
                        onProgressUpdate(temperatures_luminosities); // format : l:60,t:30
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                publishProgress(java.util.Arrays.copyOf(datas, packet.getLength()));
            }
        }

        /*
         * update of the view
         * to display the message(s) received
         */
        protected void onProgressUpdate(final String... messages){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String displayed_message = "";
                    String lum = messages[0];
                    String temp = messages[1];
                    displayed_message = "Luminosity" + lum + " Temperature" + temp;
                    if(display_direction_configuration == "TL") {
                        displayed_message = "Temperature" + temp + " Luminosity" + lum;
                    }
                    text_message.setText(displayed_message);
                }
            });
        }
    }

    /*
     * subclass to encrypt data
     */
    private class Encryption { // MODIF A TESTER 1
        private int shiftPattern;

        protected Encryption(int shiftPattern) {
            this.shiftPattern = shiftPattern;
        }

        protected String encrypt(String msg) {
            String res = "";
            for (int i = 0; i < msg.length(); i++){
                res += (char) ((int) msg.charAt(i) + this.shiftPattern);
            }
            return res;
        }

        protected String decrypt(String msg) {
            String res = "";
            for (int i = 0; i < msg.length(); i++){
                res += (char) ((int) msg.charAt(i) - this.shiftPattern);
            }
            return res;
        }
    }
}