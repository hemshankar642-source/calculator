package in.vasukinfc.billcalculator;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MainActivity extends android.app.Activity {
    private EditText amount, description, upiId, payeeName;
    private TextView total, upiLabel;
    private SharedPreferences settings;
    private ImageView qrImage;
    private Bitmap qrBitmap;
    private int pad;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        pad = dp(18);
        settings = getSharedPreferences("payment_settings", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(242,245,249));
        scroll.addView(root);

        TextView brand = title("₹  Vasuki Bill Calculator", 24, Color.rgb(15,29,51));
        root.addView(brand);
        root.addView(subtitle("Exact-amount UPI QR payment"));

        upiId = field("Your UPI ID (example@upi)", InputType.TYPE_CLASS_TEXT, settings.getString("upi_id", ""));
        payeeName = field("Your name / shop name", InputType.TYPE_CLASS_TEXT, settings.getString("payee_name", ""));
        description = field("Bill description", InputType.TYPE_CLASS_TEXT, "Payment");
        amount = field("Amount (₹)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, "0");
        root.addView(upiId); root.addView(payeeName); root.addView(description); root.addView(amount);

        total = title("₹0.00", 30, Color.WHITE);
        total.setPadding(pad, pad, pad, pad);
        total.setBackgroundColor(Color.rgb(15,29,51));
        LinearLayout.LayoutParams totalLp = new LinearLayout.LayoutParams(-1, -2);
        totalLp.setMargins(0, dp(12), 0, dp(12));
        root.addView(total, totalLp);

        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        String[][] rows = {{"7","8","9","⌫"},{"4","5","6","."},{"1","2","3","C"},{"0","Generate QR"}};
        for (String[] row : rows) {
            LinearLayout line = new LinearLayout(this);
            for (String key : row) {
                Button b = button(key, key.equals("Generate QR") ? Color.rgb(242,185,0) : Color.WHITE);
                b.setOnClickListener(v -> keyTap(key));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), key.equals("Generate QR") ? 2 : 1);
                p.setMargins(dp(3), dp(3), dp(3), dp(3)); line.addView(b,p);
            } keypad.addView(line);
        }
        root.addView(keypad);

        TextView scan = title("Scan & pay", 22, Color.rgb(15,29,51));
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(-1,-2); scanLp.setMargins(0,dp(22),0,dp(4));
        root.addView(scan,scanLp);
        upiLabel = subtitle("Add your UPI ID above");
        root.addView(upiLabel);
        qrImage = new ImageView(this); qrImage.setBackgroundColor(Color.WHITE); qrImage.setPadding(dp(10),dp(10),dp(10),dp(10));
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(-1, dp(280)); qrLp.setMargins(0,dp(12),0,dp(10));
        root.addView(qrImage,qrLp);

        root.addView(action("Open payment app", Color.rgb(15,29,51), v -> openPayment()));
        root.addView(action("Download QR", Color.rgb(242,185,0), v -> downloadQr()));
        root.addView(action("Share payment details", Color.rgb(231,236,242), v -> sharePayment()));
        root.addView(subtitle("Check payment received in your UPI app before handing over the product or service."));
        amount.setOnFocusChangeListener((v,has)->{ if(!has) generateQr(); });
        upiId.setOnFocusChangeListener((v,has)->{ if(!has) generateQr(); });
        payeeName.setOnFocusChangeListener((v,has)->{ if(!has) generateQr(); });
        generateQr();
        setContentView(scroll);
    }

    private EditText field(String hint, int type, String value) {
        EditText e = new EditText(this); e.setText(value); e.setHint(hint); e.setTextSize(16); e.setInputType(type);
        e.setBackgroundColor(Color.WHITE); e.setPadding(pad,pad/2,pad,pad/2);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(54)); p.setMargins(0,dp(8),0,0); e.setLayoutParams(p); return e;
    }
    private TextView title(String text,int size,int color){ TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setTypeface(null,1);return t; }
    private TextView subtitle(String text){ TextView t=new TextView(this);t.setText(text);t.setTextSize(14);t.setTextColor(Color.rgb(87,104,126));t.setPadding(0,dp(4),0,dp(4));return t; }
    private Button button(String text,int color){ Button b=new Button(this);b.setText(text);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(Color.rgb(20,36,59));b.setBackgroundColor(color);return b; }
    private Button action(String text,int color,View.OnClickListener click){ Button b=button(text,color);b.setOnClickListener(click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(7),0,0);b.setLayoutParams(p);return b; }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private double bill(){ try { return Math.max(0,Double.parseDouble(amount.getText().toString())); } catch(Exception e){return 0;} }
    private String configuredUpi(){ return upiId.getText().toString().trim(); }
    private String configuredName(){ String n=payeeName.getText().toString().trim(); return n.isEmpty() ? "UPI Payment" : n; }
    private void saveSettings(){ settings.edit().putString("upi_id", configuredUpi()).putString("payee_name", payeeName.getText().toString().trim()).apply(); }
    private String paymentUri(){ String note=description.getText().toString().trim(); if(note.isEmpty())note="Payment"; return "upi://pay?pa="+enc(configuredUpi())+"&pn="+enc(configuredName())+"&am="+String.format(java.util.Locale.US,"%.2f",bill())+"&cu=INR&tn="+enc(note); }
    private String enc(String s){return URLEncoder.encode(s, StandardCharsets.UTF_8);}
    private void keyTap(String key){ String old=amount.getText().toString(); if(key.equals("C"))old="0";else if(key.equals("⌫"))old=old.length()>1?old.substring(0,old.length()-1):"0";else if(key.equals("Generate QR")){generateQr();return;}else{if(old.equals("0")&&!key.equals("."))old="";if(key.equals(".")&&old.contains("."))return;old+=key;} amount.setText(old);amount.setSelection(old.length());generateQr(); }
    private void generateQr(){ double v=bill(); total.setText(String.format(java.util.Locale.US,"₹%,.2f",v)); saveSettings(); String id=configuredUpi(); upiLabel.setText(id.isEmpty() ? "Add your UPI ID above" : id); if(!id.matches(".+@.+")){ qrImage.setImageBitmap(null); qrBitmap=null; return; } try{ BitMatrix m=new QRCodeWriter().encode(paymentUri(), BarcodeFormat.QR_CODE, 700,700);Bitmap b=Bitmap.createBitmap(700,700,Bitmap.Config.RGB_565);for(int x=0;x<700;x++)for(int y=0;y<700;y++)b.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);qrBitmap=b;qrImage.setImageBitmap(b);}catch(WriterException e){Toast.makeText(this,"QR could not be created",Toast.LENGTH_SHORT).show();} }
    private boolean valid(){if(!configuredUpi().matches(".+@.+")){Toast.makeText(this,"Enter a valid UPI ID first.",Toast.LENGTH_SHORT).show();return false;}if(bill()<=0){Toast.makeText(this,"Enter a valid bill amount first.",Toast.LENGTH_SHORT).show();return false;}return true;}
    private void openPayment(){if(!valid())return;try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(paymentUri())));}catch(Exception e){Toast.makeText(this,"No UPI payment app found.",Toast.LENGTH_LONG).show();}}
    private void downloadQr(){if(!valid()||qrBitmap==null)return;try{ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"upi-qr-"+String.format(java.util.Locale.US,"%.2f",bill())+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");Uri u=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);OutputStream out=getContentResolver().openOutputStream(u);qrBitmap.compress(Bitmap.CompressFormat.PNG,100,out);out.close();Toast.makeText(this,"QR saved to Gallery.",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Could not save QR.",Toast.LENGTH_LONG).show();}}
    private void sharePayment(){if(!valid())return;Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,"Pay ₹"+String.format(java.util.Locale.US,"%.2f",bill())+" to "+configuredName()+"\nUPI ID: "+configuredUpi()+"\n"+paymentUri());startActivity(Intent.createChooser(i,"Share payment details"));}
}
