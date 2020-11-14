package com.github.michaelederaut.cygwinparser;

import java.io.IOException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.UnsupportedEncodingException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

import org.apache.commons.lang3.mutable.MutableInt;

import com.github.michaelederaut.basics.LineNbrRandomAccessFile;
import com.github.michaelederaut.basics.RegexpUtils;

import java.net.URLEncoder;

// import regexodus.Pattern;

public class ParseCygwin {

public static final String S_dnr_x86_64         = "x86_64";
public static final String S_dnr_categories_log = "_category_logs";
public static final String S_bn_setup_ini       = "setup.ini";
// public static final String S_bn_categories_log = "categories.log";
public static final String S_bnt_categories_log = "categories";

public static final String S_pna_setup_rc      = "C:\\cygwin64\\etc\\setup\\setup.rc";
public static final String S_TIME_STAMP        = "yyyy-MM-dd_HH_mm-ss";
public static final SimpleDateFormat O_timestamp_template = new SimpleDateFormat(S_TIME_STAMP);

private static String FS_get_site_root(
		final String PI_S_pna_cygw_repository_root,
		final String PI_S_dnr_site_root) {
	
	IOException      E_io;
	RuntimeException E_rt;
	File             F_dl_site_root;
	String S_msg_1, S_msg_2, S_rep_url_encoded;
	
	String S_retval_dl_site_root = null;
	
	   S_retval_dl_site_root =  PI_S_pna_cygw_repository_root + "\\" + PI_S_dnr_site_root;
	   F_dl_site_root = new File(S_retval_dl_site_root);
	   if (!F_dl_site_root.isDirectory()) {
			S_msg_1 = "Unable to loacte folder: \"" + S_retval_dl_site_root + "\"";
			E_io = new IOException(S_msg_1);
			S_msg_2 = "Unable to locate file for download-site: \"" + PI_S_dnr_site_root + "\" with \'" + SetupConfigContents.S_encoding + "\'";
			E_rt = new RuntimeException(S_msg_2, E_io);
			throw E_rt;
		    }

	return S_retval_dl_site_root;
    }


    public static void main( String[] PI_as_args ) {
   
    	IOException      E_io;
    	RuntimeException E_rt;
       
    	Date              O_timemstamp_current;
    	FileWriter        O_fwrtr_categories_log;
    	FileOutputStream  O_fs_out_categories_xslx;
    	
    	MutableInt       O_MI_nbr_lines_written;
    	
        LineNbrRandomAccessFile O_rdr_setup_ini, O_buff_wrtr_categories_log;
    	SetupIniContents O_setup_ini_contents;
    	SetupConfigContents O_config_contents;
    	SetupConfigContents.Site O_site_current;
    	ArchiveChecker   O_archive_checker;
    	
    	File F_dna_86_64, F_dna_category_logs, F_pna_setup_ini, F_pna_categories_log,  F_pna_categories_xslx;
    	String S_site_root, S_dnr_site_root, S_dna_cygw_repositories_root, S_pna_cygw_repository_root, S_dna_x86_64, S_dna_catgegory_logs, S_pna_setup_ini, 
    	S_bntd_categories /* base-name truncated dot */,  
    	S_bn_categories_log, S_bn_categories_xlsx, S_pna_categories_log, S_pna_categories_xlsx, S_time_stamp, S_msg_1, S_msg_2;
    	
    	long L_timestamp_current;
    	int I_nbr_lines_written_f1, I_flags_dummy, I_idx_mirror_f0;
    	boolean B_dir_created;
    	
    	O_config_contents = SetupConfigParser.FO_parse(S_pna_setup_rc);
    	I_idx_mirror_f0 = O_config_contents.I_idx_last_mirror_f0;
    	O_site_current = O_config_contents.AO_mirror_list.get(I_idx_mirror_f0);
    	S_dnr_site_root = O_site_current.S_dnr_url_encoded;
    	S_dna_cygw_repositories_root = O_config_contents.S_dna_last_cache;
    	
    	S_site_root = FS_get_site_root(S_dna_cygw_repositories_root, S_dnr_site_root);
    	System.out.println("Site-root: " + S_site_root);
 	
    	S_dna_x86_64         = S_site_root + "\\" + S_dnr_x86_64;
    	S_dna_catgegory_logs = S_site_root + "\\" + S_dnr_categories_log; 
    	F_dna_86_64          = new File(S_dna_x86_64);
    	F_dna_category_logs =  new File(S_dna_catgegory_logs);
    	
    	B_dir_created = false;
    	
    	try {
		   if (F_dna_category_logs.isDirectory()) {
			   B_dir_created = true; 
		       }
		   else {
			  B_dir_created = F_dna_category_logs.mkdir();
			    }
		} catch (SecurityException PI_E_sec) {
			S_msg_1 = "Error creating Folder: \"" + S_dna_catgegory_logs + "\"";
			E_rt = new RuntimeException(S_msg_1, PI_E_sec);
			throw E_rt;
		}
    	if (!B_dir_created)	{
    		S_msg_1 = "Unable to create folder: \"" + S_dna_catgegory_logs + "\"";
    		E_io = new IOException(S_msg_1);
    		S_msg_2 = "Unable to create folder containing the category logs.";
    		E_rt = new RuntimeException(S_msg_2, E_io);
    		throw E_rt;
    	    }
    	
    	if (!F_dna_86_64.isDirectory()) {
    		S_msg_1 = "Unable to locate folder: \'" + S_dna_x86_64 + "\"";
    		E_io = new IOException(S_msg_1);
    		S_msg_2 = "Unable to locate sub-folder: \'" + S_dnr_x86_64 + "\"";
    		E_rt = new RuntimeException(S_msg_2, E_io);
    		throw E_rt;
    	    }
    	
    	S_pna_setup_ini = S_dna_x86_64 + "\\" + S_bn_setup_ini;
    	F_pna_setup_ini = new File(S_pna_setup_ini);
   
    	S_msg_1         = null;
    	if (!F_pna_setup_ini.exists()) {
    		S_msg_1 = "Unable to locate file: \'" + S_pna_setup_ini + "\"";
    	    }
    	else if (!F_pna_setup_ini.isFile()) {
    		S_msg_1 = "Object \'" + S_pna_setup_ini + "\" is not a regular file"; 
    	    }
    	else if (!F_pna_setup_ini.canRead()) {
    		S_msg_1 = "File \'" + S_pna_setup_ini + "\" is not readable"; 
    	    }
    	
   	    if (S_msg_1 != null) {
    		E_io = new IOException(S_msg_1);
    	    S_msg_2 = "Unable to find readable file: \'" + S_bn_setup_ini + "\'";
    	    E_rt = new RuntimeException(S_msg_2, E_io);
    	    throw E_rt;
    	    }
    	
    	try {
			O_rdr_setup_ini = new LineNbrRandomAccessFile(F_pna_setup_ini, LineNbrRandomAccessFile.READ_ONLY);
		} catch (FileNotFoundException PI_E_fnf) {
			S_msg_1 = "Unable to instantiate a file-object of type \'" + LineNbrRandomAccessFile.class.getName() + "\' " + 
		              "from path \"" + F_pna_setup_ini.getPath() + "\"  with flag(s): \'" + LineNbrRandomAccessFile.READ_ONLY + "\'";
			E_rt = new RuntimeException(S_msg_1, PI_E_fnf);
			throw E_rt;
		    }
    	
    	O_setup_ini_contents = IniFileParser.FO_parse(O_rdr_setup_ini);      // 1
    	O_rdr_setup_ini.FV_close();
    	O_archive_checker = new ArchiveChecker(S_dna_cygw_repositories_root);
    	O_archive_checker.FI_check_pckgs(S_dnr_site_root, O_setup_ini_contents);  // 2
    	
    	L_timestamp_current = System.currentTimeMillis();
    	O_timemstamp_current = new Date(L_timestamp_current);		
    	S_time_stamp = O_timestamp_template.format(O_timemstamp_current);
    	S_bntd_categories = S_bnt_categories_log + "." + 
    	                      O_site_current.E_protocol.getName().toLowerCase()  + "_" + 
    			              O_site_current.S_site + "_" + 
    			              S_time_stamp + ".";
    	S_bn_categories_log =  S_bntd_categories + "log";
    	S_pna_categories_log = S_dna_catgegory_logs   + "\\" + S_bn_categories_log;
    	F_pna_categories_log = new File(S_pna_categories_log);
    	
    	S_bn_categories_xlsx =  S_bntd_categories + "xlsx";
    	S_pna_categories_xlsx =  S_dna_catgegory_logs + "\\" + S_bn_categories_xlsx;
    	F_pna_categories_xslx = new File(S_pna_categories_xlsx); 
    	
    	try {
			O_fs_out_categories_xslx = new FileOutputStream(F_pna_categories_xslx, false);
		} catch (FileNotFoundException PI_E_fnf) {
			S_msg_1 = "Unable to create file: \"" + S_pna_categories_xlsx + "\""; 
			E_rt = new RuntimeException(S_msg_1, PI_E_fnf);
			throw E_rt;
		}

    	O_MI_nbr_lines_written = new MutableInt(0);
    	O_archive_checker.FV_eval_categories(                     // 3
    			O_fs_out_categories_xslx,
    			S_dnr_site_root,
    			O_setup_ini_contents,
    			O_MI_nbr_lines_written);

    	I_nbr_lines_written_f1 = O_MI_nbr_lines_written.getValue();
    	S_msg_1 = I_nbr_lines_written_f1 + " rows written to worksheet: \"" + F_pna_categories_xslx.getAbsolutePath() + "\".";
    	System.out.println("\n" + S_msg_1);

    	try {
			O_fs_out_categories_xslx.close();
		} catch (IOException PI_E_io) {
			S_msg_1 = "Unable to close " + O_fs_out_categories_xslx.getClass().getSimpleName() + " \"" + F_pna_categories_xslx.getPath() + "\"";
			E_rt = new RuntimeException(S_msg_1, PI_E_io);
			throw E_rt;
		}
    }
}
