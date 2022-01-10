package com.ecoachsolutions.ecoachbooks.Core;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;

import com.ecoachsolutions.ecoachbooks.Models.BookDto;
import com.ecoachsolutions.ecoachbooks.R;
import com.ecoachsolutions.ecoachbooks.Helpers.EcoachBooksUtils;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by Daniel on 9/24/2014.
 */
public class DecryptDecompressBookTask extends AsyncTask<BookDto,Integer,BookDto> {

    Context context = null;
    ProgressDialog mProgressDialog = null;
    OnDecryptDecompressBookCompleted completionHandler = null;
    String pwd;
    View parentCallerView = null;


    public DecryptDecompressBookTask(Context ctx, ProgressDialog pd, OnDecryptDecompressBookCompleted handler, String pass, View callerView){
        completionHandler = handler;
        context = ctx;
        mProgressDialog = pd;
        pwd = pass;
        parentCallerView = callerView;
    }

    @Override
    protected void onPreExecute(){
        mProgressDialog.setMessage("Loading...");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.show();
    }


    @Override
    protected BookDto doInBackground(BookDto... dto) {
        //Get compressed file location directory
        //decrypt and unzip file
        BookDto newdto = dto[0];
        String decryptedPath = DecryptBook(newdto.getId(), pwd);
        long starttime = System.nanoTime();
        newdto.setCachePath(DecompressZipFile(decryptedPath));
        long endtime = System.nanoTime();
        Log.d("bloop", "decompression took: " + (endtime - starttime)/1e6 + "ms");
        return newdto;
    }

    @Override
    protected void onPostExecute(BookDto dto ){
        //callback parent method
        mProgressDialog.hide();
        completionHandler.BookReadyToReadCallback(dto, parentCallerView);
    }

        private String DecryptBook(String bookId, String password){
        try {
            String decKey = bookId;
            Cipher decCipher = Cipher.getInstance("AES");
            SecretKeySpec keySpec = new SecretKeySpec(EcoachBooksUtils.INSTANCE.makeKey(decKey), "AES");
            decCipher.init(Cipher.DECRYPT_MODE, keySpec);
            String outFile = EcoachBooksUtils.INSTANCE.getDecryptedFilePath(context, bookId);
            String encFile =  context.getFilesDir().getAbsolutePath() + File.separator +
                    context.getString(R.string.ecoachbooks_path) + File.separator +
                    bookId;

            //make cache dir
            String cachebasedir = context.getFilesDir().getAbsolutePath() + File.separator + context.getString(R.string.ecoachbookscache_path);
            new File(cachebasedir).mkdirs();


            //Failing here. Create DIR before anything else.
            //  File of = new File(outFile);


            //should attempt to decrypt the file on '.read()' calls
            CipherInputStream cis = new CipherInputStream(new FileInputStream(encFile), decCipher);
            CipherOutputStream cos = new CipherOutputStream(new FileOutputStream(outFile), decCipher);
            FileInputStream encFs = new FileInputStream(encFile);
            long starttime = System.nanoTime();
            // IOUtils.copy(cis, new FileOutputStream(outFile));

            byte[] buffer = new byte[8192];
            int size;
            while((size = encFs.read(buffer)) != -1){
                cos.write(buffer,0, size);
            }
            cos.flush();
            cos.close();

            long endtime = System.nanoTime();
            Log.d("bloop", "decryption and writing took: " + (endtime - starttime)/1e6 + "ms");

            return outFile;
            //with the input stream set up, we need to perform extract the contents

        } catch (Exception ex){
            ex.printStackTrace();
            return null;
        }


    }

    /**
     * Decompress a zip file to a given directory. Defaults to input pathname as a folder.
     * @param inpath
     * @return
     */
    public static String DecompressZipFile(String inpath){
        String outpath = inpath + "_tmp";
        Boolean hasOebpsfolder = false;
        Boolean hasOpsfolder = false; //some epub books use an OPS folder instead. >_<
        if (inpath == null) { return null; }

        File outdir = new File(outpath);
        outdir.mkdirs();
        try {
            if (outdir != null) {

                    ZipFile zipFile = new ZipFile(inpath);
                    Enumeration<? extends ZipArchiveEntry> entries = zipFile.getEntries();
                    while(entries.hasMoreElements()){
                        ZipArchiveEntry ze = entries.nextElement();
                        File entryDestination  = new File(outpath, ze.getName());
                        entryDestination.getParentFile().mkdirs();
                        if (!hasOebpsfolder && ze.getName().toLowerCase().contains("oebps")){
                            hasOebpsfolder = true;
                        }else if (!hasOpsfolder && ze.getName().toLowerCase().contains("ops")){
                            hasOpsfolder = true;
                        }
                        if(ze.isDirectory()){
                            entryDestination.mkdirs();
                            hasOebpsfolder = ze.getName().toLowerCase().contains("oebps") ? true : false;
                        }else{
                            InputStream in = zipFile.getInputStream(ze);
                            OutputStream os = new FileOutputStream(entryDestination);
                            IOUtils.copy(in, os);
                            IOUtils.closeQuietly(in);
                            IOUtils.closeQuietly(os);
                        }
                    }
                 //end if
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return outpath + (hasOebpsfolder ? File.separator + "OEBPS" : (hasOpsfolder ? File.separator + "OPS" : ""));
    }
}
