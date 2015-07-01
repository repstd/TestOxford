/*
Copyright (c) Microsoft Corporation
All rights reserved. 
MIT License
 
Permission is hereby granted, free of charge, to any person obtaining a copy of this 
software and associated documentation files (the "Software"), to deal in the Software 
without restriction, including without limitation the rights to use, copy, modify, merge, 
publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons 
to whom the Software is furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all copies or 
substantial portions of the Software.
THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR 
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE 
FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, 
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package com.microsoft.testoxford;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.microsoft.ProjectOxford.Contract;
import com.microsoft.ProjectOxford.DataRecognitionClient;
import com.microsoft.ProjectOxford.DataRecognitionClientWithIntent;
import com.microsoft.ProjectOxford.ISpeechRecognitionServerEvents;
import com.microsoft.ProjectOxford.MicrophoneRecognitionClient;
import com.microsoft.ProjectOxford.MicrophoneRecognitionClientWithIntent;
import com.microsoft.ProjectOxford.RecognitionResult;
import com.microsoft.ProjectOxford.RecognitionStatus;
import com.microsoft.ProjectOxford.SpeechRecognitionMode;
import com.microsoft.ProjectOxford.SpeechRecognitionServiceFactory;

import java.io.IOException;
import java.io.InputStream;

import com.microsoft.testoxford.R;

public class MainActivity extends Activity implements ISpeechRecognitionServerEvents
{
    int m_waitSeconds = 0;
    DataRecognitionClient m_dataClient = null;
    MicrophoneRecognitionClient m_micClient = null;
    boolean m_isMicrophoneReco;
    SpeechRecognitionMode m_recoMode;
    boolean m_isIntent;

    public void onPartialResponseReceived(final String response)
    {
        EditText myEditText = (EditText) findViewById(R.id.editText1);
        myEditText.append("********* Partial Result *********\n");
        myEditText.append(response + "\n");	
    }

    public void onFinalResponseReceived(final RecognitionResult response)
    {
        boolean isFinalDicationMessage = m_recoMode == SpeechRecognitionMode.LongDictation && 
                                                       (response.RecognitionStatus == RecognitionStatus.EndOfDictation ||
                                                        response.RecognitionStatus == RecognitionStatus.DictationEndSilenceTimeout);
        if (m_isMicrophoneReco && ((m_recoMode == SpeechRecognitionMode.ShortPhrase) || isFinalDicationMessage)) {
            // we got the final result, so it we can end the mic reco.  No need to do this
            // for dataReco, since we already called endAudio() on it as soon as we were done
            // sending all the data.
            m_micClient.endMicAndRecognition();
        }

        if ((m_recoMode == SpeechRecognitionMode.ShortPhrase) || isFinalDicationMessage) {
            Button startButton = (Button) findViewById(R.id.button1);
            startButton.setEnabled(true);
        }

        if (!isFinalDicationMessage) {
            EditText myEditText = (EditText) findViewById(R.id.editText1);
            myEditText.append("***** Final NBEST Results *****\n");
            for (int i = 0; i < response.Results.length; i++) {
                myEditText.append(i + " Confidence=" + response.Results[i].Confidence + 
                                  " Text=\"" + response.Results[i].DisplayText + "\"\n");
            }
    	}
    } 

    /**
    * Called when a final response is received and its intent is parsed 
    */
    public void onIntentReceived(final String payload)
    {
        EditText myEditText = (EditText) findViewById(R.id.editText1);
        myEditText.append("********* Final Intent *********\n");
        myEditText.append(payload + "\n");
    }
    
    public void onError(final int errorCode, final String response) 
    {
        Button startButton = (Button) findViewById(R.id.button1);
        startButton.setEnabled(true);
        
        EditText myEditText = (EditText) findViewById(R.id.editText1);
        myEditText.append("********* Error Detected *********\n");
        myEditText.append(errorCode + " " + response + "\n");
    }

    /**
     * Invoked when the audio recording state has changed.
     *
     * @param recording The current recording state
     */
    public void onAudioEvent(boolean recording)
    {
    	if (!recording) {
    		m_micClient.endMicAndRecognition();
            Button startButton = (Button) findViewById(R.id.button1);
            startButton.setEnabled(true);
    	}
    	
        EditText myEditText = (EditText) findViewById(R.id.editText1);
        myEditText.append("********* Microphone status: " + recording + " *********\n");
    }

    /**
    * Speech recognition with data (for example from a file or audio source).  
    * The data is broken up into buffers and each buffer is sent to the Speech Recognition Service.
    * No modification is done to the buffers, so the user can apply their
    * own VAD (Voice Activation Detection) or Silence Detection
    * 
    * @param dataClient
    * @param speechRecognitionMode
    */
    void doDataRecognition(DataRecognitionClient dataClient, SpeechRecognitionMode recoMode)
    {
        try {           
            // Note for wave files, we can just send data from the file right to the server.
            // In the case you are not an audio file in wave format, and instead you have just
            // raw data (for example audio coming over bluetooth), then before sending up any 
            // audio data, you must first send up an SpeechAudioFormat descriptor to describe 
            // the layout and format of your raw audio data via DataRecognitionClient's sendAudioFormat() method.
            String filename = recoMode == SpeechRecognitionMode.ShortPhrase ? "whatstheweatherlike.wav" : "batman.wav";
            InputStream fileStream = getAssets().open(filename);
            int bytesRead = 0;
            byte[] buffer = new byte[1024];
              
            do {
                // Get  Audio data to send into byte buffer.
                bytesRead = fileStream.read(buffer);

                if (bytesRead > -1) {
                    // Send of audio data to service. 
                    dataClient.sendAudio(buffer, bytesRead);
                }
            } while (bytesRead > 0);
        }
        catch(IOException ex) {
            Contract.fail();
        }
        finally {            
            dataClient.endAudio();
        }		    
    }

    void initializeRecoClient()
    {
        String language = "en-us";
        
        String primaryOrSecondaryKey = this.getString(R.string.primaryKey);
        String luisAppID = this.getString(R.string.luisAppID);
        String luisSubscriptionID = this.getString(R.string.luisSubscriptionID);

        if (m_isMicrophoneReco && null == m_micClient) {
            if (!m_isIntent) {
                m_micClient = SpeechRecognitionServiceFactory.createMicrophoneClient(this,
                                                                                     m_recoMode, 
                                                                                     language,
                                                                                     this,
                                                                                     primaryOrSecondaryKey);
            }
            else {
                MicrophoneRecognitionClientWithIntent intentMicClient;
                intentMicClient = SpeechRecognitionServiceFactory.createMicrophoneClientWithIntent(this,
                                                                                                   language,
                                                                                                   this,
                                                                                                   primaryOrSecondaryKey,
                                                                                                   luisAppID,
                                                                                                   luisSubscriptionID);
                m_micClient = intentMicClient;

            }
        }
        else if (!m_isMicrophoneReco && null == m_dataClient) {
            if (!m_isIntent) {
                m_dataClient = SpeechRecognitionServiceFactory.createDataClient(this,
                                                                                m_recoMode, 
                                                                                language,
                                                                                this,
                                                                                primaryOrSecondaryKey);
            }
            else {
                DataRecognitionClientWithIntent intentDataClient;
                intentDataClient = SpeechRecognitionServiceFactory.createDataClientWithIntent(this, 
                                                                                              language,
                                                                                              this,
                                                                                              primaryOrSecondaryKey,
                                                                                              luisAppID,
                                                                                              luisSubscriptionID);
                m_dataClient = intentDataClient;
            }
        }
    }

    void addListenerOnButton() 
    {
        final Button startButton = (Button) findViewById(R.id.button1); 
        startButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) 
            {             
                EditText myEditText = (EditText) findViewById(R.id.editText1);
                myEditText.setText("");
                startButton.setEnabled(false);

                if (m_isMicrophoneReco) {
                    // Speech recognition from the microphone.  The microphone is turned on and data from the microphone
                    // is sent to the Speech Recognition Service.  A built in Silence Detector
                    // is applied to the microphone data before it is sent to the recognition service.
                    m_micClient.startMicAndRecognition();	
                }
                else {                    
                    doDataRecognition(m_dataClient, m_recoMode);
                } 
            }
        });

        final Context appContext = this;
        Button finalResponseButton = (Button) findViewById(R.id.button2);
		
        finalResponseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) 
            {
                AlertDialog alertDialog;
                alertDialog = new AlertDialog.Builder(appContext).create();
                alertDialog.setTitle("Final Response");
                
                boolean isReceivedResponse = false;
                if (m_micClient != null) {
                    isReceivedResponse = m_micClient.waitForFinalResponse(m_waitSeconds);
                    m_micClient.endMicAndRecognition();
                    String msg = isReceivedResponse ? "See TextBox below for response.  App Done" : "Timed out.  App Done";
                    alertDialog.setMessage(msg);
                    startButton.setEnabled(false);
                    m_micClient.dispose();
                }
                else if (m_dataClient != null) {
                    isReceivedResponse = m_dataClient.waitForFinalResponse(m_waitSeconds);
                    String msg = isReceivedResponse ? "See TextBox below for response.  App Done" : "Timed out.  App Done";
                    alertDialog.setMessage(msg);
                    startButton.setEnabled(false);
                    m_dataClient.dispose();
                }
                else {
                    alertDialog.setMessage("Press Start first please!");
                }
                alertDialog.show();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); 
        
        // Set the mode and microphone flag to your liking   
        m_recoMode = SpeechRecognitionMode.ShortPhrase;
        m_isMicrophoneReco = false;
        m_isIntent = false;

        m_waitSeconds = m_recoMode == SpeechRecognitionMode.ShortPhrase ? 20 : 200;
        
        initializeRecoClient();
        
        // setup the buttons
        addListenerOnButton();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
