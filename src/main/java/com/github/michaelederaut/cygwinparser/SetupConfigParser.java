package com.github.michaelederaut.cygwinparser;

import java.io.IOException;
import java.util.Stack;

import com.github.michaelederaut.basics.LineNbrRandomAccessFile;
import com.github.michaelederaut.basics.RegexpUtils;
import com.github.michaelederaut.cygwinparser.SetupConfigContents.Site;

import java.io.File;
import java.io.FileNotFoundException;

import regexodus.Pattern;

public class SetupConfigParser {

	public static final String S_pna_setup_rc = "C:\\cygwin64\\etc\\setup\\setup.rc";
	public static final String S_re_payload = "^\\s{1,}([\\S]+)\\s*$";
	public static final Pattern P_payload   = new Pattern(S_re_payload);
	public static final String S_re_mirror_site = "^\\s{1,}([^;]+)\\;([^;]+)\\;([^;]+)\\;([^;]+)\\s*$";
	public static final Pattern P_mirror_site   = new Pattern(S_re_mirror_site);
	
	public static final String LAST_CACHE   = "last-cache";
	public static final String MIRRORS_LIST = "mirrors-lst";
	public static final String LAST_MIRROR  = "last-mirror";

	private enum ParsingState {Init, LastCache, MirrorsList, LastMirror, Finished};
	private final static int  I_parsing_state_finished      = ParsingState.Finished.ordinal();
	
	public static LineNbrRandomAccessFile  F_setup_rc; //  = new LineNbrRandomAccessFile(S_pna_setup_rc, "r");
	
	public static SetupConfigContents FO_parse (final String PI_S_pna_setup_rc) {
		 
		 IllegalStateException E_ill_state;
		 RuntimeException      E_rt;
	     AssertionError        E_assert;
		 Stack<Site> AO_mirror_list;
		 ParsingState E_parsing_state;
		 Site O_site;
		 String S_msg_1, S_msg_2, S_line_input, AS_numbered_groups[],
		 S_pna_last_cache, S_url_last_mirror, S_url_mirror_site, S_mirror_host, S_region, S_state ;
		 
		 int I_line_nbr_f1, i1, I_idx_last_mirror_f0, I_nbr_mirrors_f1;
		 RegexpUtils.GroupMatchResult O_group_match_result;
			
		 SetupConfigContents O_retval_setup_config_contents = null;

		 try {
			 F_setup_rc = new LineNbrRandomAccessFile(S_pna_setup_rc, LineNbrRandomAccessFile.READ_ONLY);
			} catch (FileNotFoundException PI_E_fnf) {
				S_msg_1 = "Unable to instantiate a file-object of type \'" + LineNbrRandomAccessFile.class.getName() + "\' " + 
			              "from path \"" + F_setup_rc.S_pn + "\"  with flag(s): \'" + LineNbrRandomAccessFile.READ_ONLY + "\'";
				E_rt = new RuntimeException(S_msg_1, PI_E_fnf);
				throw E_rt;
			    }
		 
		I_line_nbr_f1               = 0;
		S_pna_last_cache            = null;
		AO_mirror_list  = new Stack<Site>();
		S_url_last_mirror = null;
		I_idx_last_mirror_f0 = -1;
		E_parsing_state = ParsingState.Init;
		LOOP_INPUT_LINES: while (E_parsing_state.ordinal() < I_parsing_state_finished) {
			try {
				S_line_input = F_setup_rc.FS_readLine();
				I_line_nbr_f1 = F_setup_rc.I_curr_line_nbr;
			} catch (RuntimeException PI_E_rt) {
				S_msg_1 = "Error reading input file at line: " + I_line_nbr_f1;
				E_rt = new RuntimeException(S_msg_1, PI_E_rt);
				throw E_rt;
			    }
			if (S_line_input == null) {
				E_parsing_state = ParsingState.Finished;
				break LOOP_INPUT_LINES;
		        }
		 if (E_parsing_state == ParsingState.Init) {
			 if (S_line_input.startsWith(LAST_CACHE)) {
				 E_parsing_state = ParsingState.LastCache;	 
			     }
		      }
		 else if (E_parsing_state == ParsingState.LastCache) {
			 O_group_match_result = RegexpUtils.FO_match(S_line_input, P_payload);
			 if (O_group_match_result.I_array_size_f1 >= 2) {
				 S_pna_last_cache = O_group_match_result.AS_numbered_groups[1];
			    }
			 else if (S_line_input.startsWith(MIRRORS_LIST)) {
				 E_parsing_state = ParsingState.MirrorsList;
			     }
			 else {
				 S_msg_1 = "Parsing error when searching for " + MIRRORS_LIST + " at line: " + I_line_nbr_f1;
				 E_ill_state = new IllegalStateException(S_msg_1);
				 S_msg_2 = "Error when parsing: \"" + F_setup_rc.S_pn + "\"";
				 E_rt = new RuntimeException(S_msg_2, E_ill_state);
				 throw E_rt; 
			     }
		     }
		 else if (E_parsing_state == ParsingState.MirrorsList) {
			 O_group_match_result = RegexpUtils.FO_match(S_line_input, P_mirror_site);
			 if (O_group_match_result.I_array_size_f1 >= 5) {
			     AS_numbered_groups    = O_group_match_result.AS_numbered_groups;
			     S_url_mirror_site     = AS_numbered_groups[1];
			     S_mirror_host         = AS_numbered_groups[2];
			     S_region              = AS_numbered_groups[3];
			     S_state               = AS_numbered_groups[4];
			     O_site = new Site(S_url_mirror_site, S_mirror_host, S_region, S_state);
			     AO_mirror_list.push(O_site);
			     }
			 else if (S_line_input.startsWith(LAST_MIRROR)) {
				 E_parsing_state = ParsingState.LastMirror;
			     }
		      }
		 else if (E_parsing_state == ParsingState.LastMirror) {
			 O_group_match_result = RegexpUtils.FO_match(S_line_input, P_payload);
			 if (O_group_match_result.I_array_size_f1 >= 2) {
				 S_url_last_mirror = O_group_match_result.AS_numbered_groups[1];
				 I_nbr_mirrors_f1 = AO_mirror_list.size(); 
				 LOOP_MIRRORS: for (i1 = 0; i1 < I_nbr_mirrors_f1; i1++) {
					 O_site = AO_mirror_list.get(i1);
					 S_url_mirror_site = O_site.O_url_download_source.toExternalForm();
					 if (S_url_mirror_site.equals(S_url_last_mirror)) {
						 I_idx_last_mirror_f0 = i1;
						 E_parsing_state = ParsingState.Finished;
						 break LOOP_MIRRORS;
					     }
				    }
		      } // END if
		    }   // END elsif
		}
		if (I_idx_last_mirror_f0 >= 0) {
			O_retval_setup_config_contents = new SetupConfigContents();
			O_retval_setup_config_contents.S_pna_last_cache     = S_pna_last_cache;
			O_retval_setup_config_contents.AO_mirror_list       = AO_mirror_list;
			O_retval_setup_config_contents.S_url_last_mirror    = S_url_last_mirror;
			O_retval_setup_config_contents.I_idx_last_mirror_f0 = I_idx_last_mirror_f0;
		    }
		F_setup_rc.FV_close();
		 
		return O_retval_setup_config_contents;
	 }   // END FO_parse ...
}
