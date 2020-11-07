package com.github.michaelederaut.cygwinparser;

import java.awt.Color;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.NavigableSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.text.StrBuilder;
import org.apache.logging.log4j.core.util.ArrayUtils;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FontFamily;
import org.apache.poi.ss.usermodel.FontUnderline;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import com.github.michaelederaut.basics.LineNbrRandomAccessFile;
import com.github.michaelederaut.basics.RegexpUtils;
import com.github.michaelederaut.basics.RegexpUtils.GroupMatchResult;
import com.github.michaelederaut.basics.RegexpUtils.NamedPattern;
import com.github.michaelederaut.cygwinparser.SetupIniContents.ArchInfo;
import com.github.michaelederaut.cygwinparser.SetupIniContents.ArchInfo.DlStatus;
import com.github.michaelederaut.cygwinparser.SetupIniContents.PckgInfo;
import com.github.michaelederaut.cygwinparser.SetupIniContents.PckgVersionInfo;
import com.github.michaelederaut.cygwinparser.SetupIniContents.PckgArchInfos;

public class ArchiveChecker {

	protected enum PckgVersion {current, prev} ;
	protected enum ArchPurpose {install, source};
	
	public enum CompletionDegree {
			
		incompleteInstall("install-incomplete"), // no valid or only prev installation archive found
		completeInstall("install-complete"), // valid installation archive found but no valid or only prev source archive found
		completeSrc("source-complete"); // valid installation archive  
	 
		public static final String PREFIX = "by-category_";
		public String S_description;
		
		CompletionDegree(final String PI_S_description) {
			this.S_description = PREFIX + PI_S_description;
		    }
		}; 
	
    public static final int I_nbr_compl_degrees = CompletionDegree.values().length;
    
    public enum PurposeCompletionDegrees {
		notFound, unknownVersion, prevOk, currentOk 
	    };
    
	public static final int I_nbr_purpose_completion_degrees = PurposeCompletionDegrees.values().length;
			  
	protected static final int DL_MINIMUM_REQUIREMENT = SetupIniContents.ArchInfo.DlStatus.sizeOk.ordinal();
	// protected static final int DL_MINIMUM_REQUIREMENT = SetupIniContents.ArchInfo.DlStatus.exists.ordinal();
	
	protected static final int I_min_nbr_archive_fn_parts = 4;
	protected static final String S_archive_suffixes = "xz|bz2";
	protected static final String S_re_pckg_install = "^\\-(\\S+?)\\.(tar\\.("       + S_archive_suffixes + "))$";
	protected static final String S_re_pckg_src     = "^\\-(\\S+?)\\-(src\\.tar\\.(" + S_archive_suffixes + "))$";
	protected static final NamedPattern P_pckg_install = new NamedPattern(S_re_pckg_install);
	protected static final NamedPattern P_pckg_src = new NamedPattern(S_re_pckg_src);
	protected static final String S_work_sheet_name = "install incomplete";
	protected static final String AS_header_row[] = 
			new String[] {"Name" , 
					  "expected version", 
					  "install:" + System.lineSeparator() + "status / version found", 
			          "source:"  + System.lineSeparator() + "status / version found",
			          "installation folder", 
			          "source folder" +  System.lineSeparator() + "if different from installation folder"};
	protected static final int I_nbr_cols_f1 = AS_header_row.length;  
	protected String S_last_category;
	
	protected XSSFFont         O_font_bold, O_font_hlink;
	protected XSSFCellStyle    O_cell_style_bold, O_cell_style_bold_wrap, O_cell_style_hlink;
	protected CreationHelper   O_creation_helper;
//	protected XSSFHyperlink    O_hyper_link;
	protected XSSFColor        O_color_blue;
	
	public String S_dna_cygw_repository_root;
	
	protected class PckgNameFilter {
		
		public String S_prefix;
	    public ArchPurpose E_arch_purpose;
	    public NamedPattern P_tail;
	    public Stack<String> AS_versions;
			  
	    protected PckgNameFilter(
				final String PI_S_prefix,
				final ArchPurpose PI_E_arch_purpose) {
			
			this.S_prefix = PI_S_prefix;
			this.E_arch_purpose = PI_E_arch_purpose;
			if (PI_E_arch_purpose == ArchPurpose.install) {
			   this.P_tail = P_pckg_install;	
			   }
			else {
			   this.P_tail = P_pckg_src;
			    }
			this.AS_versions = new Stack<String>();
		    }	
	    
	    
	   	public BiPredicate<Path, BasicFileAttributes> FB_is_regular =
        		  (Path PI_O_path,  BasicFileAttributes PI_attrs) ->   {
        			  
        	 GroupMatchResult O_grp_match_result;		  
        	 Path P_bn;
        	 int I_len_prefix;		  
        	 String S_pn, S_bn, S_suffix, S_version;
        	
        	 boolean B_retval = false;
        	  
        	 if (!PI_attrs.isRegularFile()) {
        	 	 return B_retval;
        	     }
        	 P_bn = PI_O_path.getFileName();
        	 S_bn = P_bn.toString();
        	  
             if (!S_bn.startsWith(this.S_prefix)) {
            	return B_retval;
                }
              I_len_prefix = this.S_prefix.length();
              S_suffix = S_bn.substring(I_len_prefix);
              O_grp_match_result = RegexpUtils.FO_match(S_suffix, this.P_tail.O_patt);
              if (O_grp_match_result.I_array_size_f1 < 4) {
            	  return B_retval;
                  }
              S_version = O_grp_match_result.AS_numbered_groups[1];
              if (this.E_arch_purpose == ArchPurpose.install) {
			  	  if (S_version.endsWith("-src")) {
			         return B_retval;
			    	 }
			      }
              this.AS_versions.push(S_version);
			  B_retval = true;
	          return B_retval;
	    };
	}
	
//----------------------
	
	protected static class ArchPurposeContents {
		SetupIniContents.ArchInfo.DlStatus E_dl_status;
		String S_version_found, S_hyperlink_txt, S_hyperlink_destination;
		
		public ArchPurposeContents (
				final SetupIniContents.ArchInfo.DlStatus PI_E_dl_status,
				final String                             PI_S_version_found,
				final String                             PI_S_hyperlink_txt,
				final String                             PI_S_hyperlink_destination) {
			
			this.E_dl_status              = PI_E_dl_status;
			this.S_version_found          = PI_S_version_found;
			this.S_hyperlink_txt          = PI_S_hyperlink_txt;
			this.S_hyperlink_destination  = PI_S_hyperlink_destination;
			this.S_hyperlink_destination = this.S_hyperlink_destination.replace("\\", "/");
		    return;
			}
	}
	
	protected static class RowContents {
		public static enum Type {category, pckg}; 
		
		public Type E_type;
		public String S_name;
		
		public String S_version_requested;
		ArchPurposeContents AO_arch_purpose[];
		
	 public RowContents(final String PI_S_category) {
		this.E_type      = Type.category;
		this.S_name      = PI_S_category;	
		}
	 
	 public RowContents(
			 final String PI_S_pckg_name,
			 final String PI_S_version_requested,
			 final ArchPurposeContents PI_AO_arch_purpose[]) {
		 this.E_type              = Type.pckg;
		 this.S_name              = PI_S_pckg_name;
		 this.S_version_requested = PI_S_version_requested;
		 this.AO_arch_purpose     = PI_AO_arch_purpose;  
	     }
	}
	
	public ArchiveChecker (final String PI_S_dna_cygw_repository_root) {
		this.S_dna_cygw_repository_root = PI_S_dna_cygw_repository_root;
	    }
	
	protected int FI_check_pckg_archives(
			  final String          PI_S_dnr_site,
			//  final ArchInfo       PB_O_arch_info,
			  final PckgArchInfos  PB_O_pckg_arch_infos,
			 
			  final int             PI_I_pckg_nr_f0,
			  final PckgVersion     PI_E_ver,
			  final ArchPurpose     PI_E_purpose) {
		
		  AssertionError E_assert;
		  RuntimeException E_rt;
		  
		  File F_pna_archive, F_dna_archives;
		  Path FP_dna_archives;
		  
		  NamedPattern P_pckg;
		  GroupMatchResult O_grp_match_result;
		  PckgNameFilter   O_file_filter;
		  List<String> AS_vers_prev, AS_bn_prev;
		  Object AO_bn_prev[];
		  ArchInfo O_arch_info;
		  String S_msg_1, S_msg_2, S_pna_archive, S_pnr_archive, S_pckg_name, S_bn_archive_requested, 
		         S_version_received, S_bn_tail, S_bn_suffix, S_arch_type,
		          AS_archive_pnr_parts[], /* AS_bn_prev[], */ S_vers_prev;  
	      int I_retval_nbr_checked_archives, I_nbr_archive_fn_parts_f1, I_idx_pckg_name_f0, I_idx_bn_archive_f0, 
	          I_len_dnr_pckg_f1, I_nbr_prev_versions_f1, I_idx_completion_category_f0;
	      long L_size_actual, L_size_expected;
	      
	      final BiPredicate<Path, BasicFileAttributes> F_B_is_regular =
        		  (Path PI_O_path,  BasicFileAttributes PI_attrs) ->   {
        			 String S_pn;
        			 boolean B_retval = false;
        			 if (PI_attrs.isRegularFile()) {
        				 S_pn = PI_O_path.toString();
        		//		 if (P_pckg_install.O_patt.ma)
        			 };
	    			  return B_retval;
	    		  };  
	         
	      I_retval_nbr_checked_archives = 0;
	      
	      S_pnr_archive = PB_O_pckg_arch_infos.S_pnr_archive;
	      S_pna_archive = this.S_dna_cygw_repository_root + "\\" + PI_S_dnr_site + "\\" + S_pnr_archive;
	      
	      AS_archive_pnr_parts = S_pnr_archive.split("/");
	      I_nbr_archive_fn_parts_f1 = ArrayUtils.getLength(AS_archive_pnr_parts);
	      if (I_nbr_archive_fn_parts_f1 < I_min_nbr_archive_fn_parts) {
	    	 S_msg_1 = "Pathname \"" + S_pna_archive + "\" doesnt have the minimun required " + I_min_nbr_archive_fn_parts + " parts.";
	    	 E_assert = new AssertionError(S_msg_1);
	    	 S_msg_2 = "Unable to determine package name from package indexed " + PI_I_pckg_nr_f0;
	    	 E_rt = new RuntimeException(S_msg_2, E_assert);
	    	 throw E_rt;
	    	 }
	    	 
	     LOOP_COMPLETION_CATEGORIES:
	     for (I_idx_completion_category_f0 = 0; 
	          I_idx_completion_category_f0 < I_nbr_purpose_completion_degrees; 
	    	  I_idx_completion_category_f0++) {
	    	  
	          O_arch_info = PB_O_pckg_arch_infos.AAO_archinfos[I_idx_completion_category_f0];
	          if (O_arch_info == null) {
	        	  continue LOOP_COMPLETION_CATEGORIES;
	              }
		      O_arch_info.S_ver_locally_stored = null;
		      I_idx_bn_archive_f0 = I_nbr_archive_fn_parts_f1 - 1;
		      I_idx_pckg_name_f0  = I_nbr_archive_fn_parts_f1 - 2;
		      S_pckg_name = AS_archive_pnr_parts[I_idx_pckg_name_f0];
		      S_bn_archive_requested = AS_archive_pnr_parts[I_idx_bn_archive_f0];
		      I_len_dnr_pckg_f1 = S_pckg_name.length();
		      S_bn_tail = S_bn_archive_requested.substring(I_len_dnr_pckg_f1);
		      if (PI_E_purpose == ArchPurpose.install) {
		         P_pckg = P_pckg_install;
		    	 }
		      else {
		    	 P_pckg = P_pckg_src;
		    	   }
		      O_grp_match_result = RegexpUtils.FO_match(S_bn_tail, P_pckg.O_patt);     
		    	    
		      F_pna_archive = new File(S_pna_archive);
		      if (F_pna_archive.exists()) {
		    	  I_retval_nbr_checked_archives = 1;   
		    	  O_arch_info.E_dl_status = DlStatus.exists; 
		          }
		      else {  
		         if (PI_E_ver == PckgVersion.current) { // check if there are (previous/other) versions of this archive in the same folder
		    	    if (O_grp_match_result.I_array_size_f1 >= 4) {
		    		   S_version_received = O_grp_match_result.AS_numbered_groups[1];
		    		   S_bn_suffix         = O_grp_match_result.AS_numbered_groups[2];
		    		   S_arch_type         = O_grp_match_result.AS_numbered_groups[3];
		//    		   O_file_filter = new PckgNameFilter(S_pckg_name, PI_E_purpose);
		    		   O_file_filter =  new PckgNameFilter(S_pckg_name, PI_E_purpose);
		    		  
		    		  F_dna_archives = F_pna_archive.getParentFile();
		    		  if (F_dna_archives.isDirectory()) {
		    			  // https://stackoverflow.com/questions/2056221/recursively-list-files-in-java
		    			  FP_dna_archives = Paths.get(F_dna_archives.getAbsolutePath());
		    			  try {
							AO_bn_prev = Files.find(FP_dna_archives, 3, O_file_filter.FB_is_regular).collect(Collectors.toList()).toArray();
						} catch (IOException|ClassCastException PI_E_io) {
							S_msg_1 = "Unable to find archives under \"" + F_dna_archives.getAbsolutePath();
							E_rt = new RuntimeException(S_msg_1, PI_E_io);
							throw E_rt;
						}
			    		//  AS_bn_prev = F_dna_archives.list(O_file_filter);
			    		I_nbr_prev_versions_f1 =  AO_bn_prev.length;
			    		if (I_nbr_prev_versions_f1 > 0) {
			    			 AS_vers_prev = O_file_filter.AS_versions;
			    			 S_vers_prev = AS_vers_prev.get(0);
			    		     O_arch_info.E_dl_status = DlStatus.prev;
			    		     O_arch_info.S_ver_locally_stored = S_vers_prev;
			    		     return I_retval_nbr_checked_archives;
			    	         }
		    		      }
		    	      }
		    	    } // END version == current
		         } // END archive exists
		      
		      if (F_pna_archive.isFile()) {
		    	  I_retval_nbr_checked_archives = 1;   
		    	  O_arch_info.E_dl_status = DlStatus.isFile;
		    	  if (O_grp_match_result.I_array_size_f1 >= 4) {
		    		 S_version_received = O_grp_match_result.AS_numbered_groups[1];
		    		 O_arch_info.S_ver_locally_stored = S_version_received;
		    	     }
		          }
		      else {
		    	  return I_retval_nbr_checked_archives;
		          }
		      
		      L_size_actual = F_pna_archive.length();
		      L_size_expected = O_arch_info.I_size;
		      if (L_size_actual == L_size_expected) {
		    	 O_arch_info.E_dl_status = DlStatus.sizeOk; 
		         }
		      else {
		    	  return I_retval_nbr_checked_archives;
		          }
		       } // END LOOP LOOP_COMPLETION_CATEGORIES
	      return I_retval_nbr_checked_archives;
	      } 
	
	
	protected int FI_check_pckg_version(
			  final String          PI_S_dnr_site,
			  final PckgArchInfos PI_O_pckg_arch_infos,
			  final int             PI_I_pckg_nr_f0,
			  final PckgVersion     PI_E_ver) {
		
		      ArchInfo  AO_archinfo[];
		      int I_retval_nbr_checked_archives, I_res_nbr_f1;
		      
		      
		      I_retval_nbr_checked_archives = 0;
		      
		   //   AO_archinfo = PI_O_pckg_ver_info.O_install;
		   //   if (O_archinfo != null) {
		         I_res_nbr_f1 = FI_check_pckg_archives(
		    		  PI_S_dnr_site,
		    		  PI_O_pckg_arch_infos,
		    		  PI_I_pckg_nr_f0,
		    		  PI_E_ver,
		    		  ArchPurpose.install
		    		  );
		          I_retval_nbr_checked_archives += I_res_nbr_f1;
		    //      }
		      
		   //   AO_archinfo = PI_O_pckg_ver_info.AO_src;
		   //   if (O_archinfo != null) {
		         I_res_nbr_f1 = FI_check_pckg_archives(
		    		  PI_S_dnr_site,
		    		  PI_O_pckg_arch_infos,
		    		  PI_I_pckg_nr_f0,
		    		  PI_E_ver,
		    		  ArchPurpose.source
		    		  );  
		          I_retval_nbr_checked_archives += I_res_nbr_f1;
		    //      }
		      return I_retval_nbr_checked_archives;     
	}
	
	public int FI_check_pckgs(
			final String           PI_S_dnr_site,
			final SetupIniContents PB_O_setup_ini_contents) {
		
		PckgInfo O_pckg_info;
		PckgVersionInfo O_pckg_ver_info_curr, O_pckg_ver_info_prev;
		int I_retval_nbr_checked_archives, I_nbr_pckgs_f1, I_res_nbr_f1, i1;
		
		I_retval_nbr_checked_archives = 0;
		
		I_nbr_pckgs_f1 = PB_O_setup_ini_contents.AO_pckg_info.size();
		for (i1 = 0; i1 < I_nbr_pckgs_f1; i1++) {
			O_pckg_info = PB_O_setup_ini_contents.AO_pckg_info.get(i1);
			O_pckg_ver_info_curr = O_pckg_info.O_version_current;
			
			I_res_nbr_f1 = FI_check_pckg_version(
					PI_S_dnr_site,
					O_pckg_ver_info_curr,
					i1,
					PckgVersion.current);
			I_retval_nbr_checked_archives += I_res_nbr_f1;  
			
			O_pckg_ver_info_prev = O_pckg_info.O_version_prev;
			if (O_pckg_ver_info_prev != null) {
				I_res_nbr_f1 = FI_check_pckg_version(
						PI_S_dnr_site,
						O_pckg_ver_info_prev,
						i1,
						PckgVersion.prev);
				I_retval_nbr_checked_archives += I_res_nbr_f1;  
			    }
		    }		
		return I_retval_nbr_checked_archives;	
	}

	public void FV_write_row (
			final Row PO_O_row,
			final RowContents PI_O_row_contents) {
//		AssertionError E_assert;
		RuntimeException E_rt;
		
		// File F_hyperlink_destination;
		XSSFHyperlink    O_hyper_link;
		Cell O_cell;
		RowContents.Type E_type;
		
		DlStatus E_dl_status;
		ArchPurposeContents O_arch_purpose_contents, AO_arch_purpose_contents[];
		String S_pckg_name, S_version_requested, S_version_found, 
		       S_hyperlink_install,  S_hyperlink_src, /* S_hyperlink_destination, */ S_msg_1;
		int I_nbr_purposes_f1, i1, I_col_idx_f0;
		
		E_type = PI_O_row_contents.E_type;
		O_cell = PO_O_row.createCell(0, CellType.STRING);
		if (E_type == RowContents.Type.category) {
		    S_last_category = PI_O_row_contents.S_name;
			O_cell.setCellValue(S_last_category);
			O_cell.setCellStyle(O_cell_style_bold);
		    }
		else {
			AO_arch_purpose_contents = PI_O_row_contents.AO_arch_purpose;
			S_pckg_name         = PI_O_row_contents.S_name;
			O_cell.setCellValue(S_pckg_name);
			
			S_version_requested = PI_O_row_contents.S_version_requested;
			O_cell = PO_O_row.createCell(1, CellType.STRING);
			O_cell.setCellValue(S_version_requested);
			I_nbr_purposes_f1 = AO_arch_purpose_contents.length;
			I_col_idx_f0 = 2;
			S_hyperlink_install = null;
			LOOP_CELL_COLS: for (i1 = 0; i1 < I_nbr_purposes_f1; i1++) {
				S_version_found = null;
				O_arch_purpose_contents = AO_arch_purpose_contents[i1];
				if (O_arch_purpose_contents != null) {
				   O_cell = PO_O_row.createCell(I_col_idx_f0, CellType.STRING);
				   E_dl_status = O_arch_purpose_contents.E_dl_status;
				   if (E_dl_status == DlStatus.prev) {
				       S_version_found = O_arch_purpose_contents.S_version_found; 
				       }
				   else {
					   S_version_found = E_dl_status.name();
				       }
				   O_cell.setCellValue(S_version_found);
				   
				   if (i1 == 0) {
					   S_hyperlink_install = O_arch_purpose_contents.S_hyperlink_txt;
				       }
				   else {
					   S_hyperlink_src = O_arch_purpose_contents.S_hyperlink_txt;
					   if (StringUtils.isBlank(S_hyperlink_src)) {
						  break LOOP_CELL_COLS; 
					      }
					   if (StringUtils.equals(S_hyperlink_install, S_hyperlink_src)) {
						  break LOOP_CELL_COLS;  
					      }
				       }
				   O_cell = PO_O_row.createCell(I_col_idx_f0 + 2, CellType.STRING);
				   O_cell.setCellValue(O_arch_purpose_contents.S_hyperlink_txt);
   			       O_hyper_link = (XSSFHyperlink)this.O_creation_helper.createHyperlink(HyperlinkType.FILE);
				   O_hyper_link.setAddress(/* "file:///" + */ O_arch_purpose_contents.S_hyperlink_destination);
				   O_cell.setHyperlink((org.apache.poi.ss.usermodel.Hyperlink)O_hyper_link);
				   O_cell.setCellStyle(O_cell_style_hlink);
				   }
				I_col_idx_f0++;	  
			    }
			return;
		}
		return;
	}
	
	public void FV_eval_categories(
			final FileOutputStream PB_O_fs_output,
			final String           PI_S_dnr_site,
			final SetupIniContents PI_O_setup_ini_contents,
			final MutableInt       PB_I_nbr_lines_written) {
		
		 RuntimeException                  E_rt;
		 AssertionError                    E_assert;
		 NullPointerException              E_np;
		 TreeMap<String, TreeSet<String>>  HAS_categories;
		 NavigableSet<String>              AS_categories, AS_packages;
		
		// http://poi.apache.org/spreadsheet/quick-guide.html#NewWorkbook
		// https://www.tutorialspoint.com/apache_poi/apache_poi_hyperlink.htm
		 XSSFWorkbook                      O_wb;
		 Sheet                             O_work_sheet;
		 Row                               O_row;
		 Cell                              O_cell;
		
		 ArchInfo                          O_arch_info_install, O_arch_info_src;
		 PckgInfo                          O_pckg_info;
		 PckgVersionInfo                   O_pckg_vers_info;
	//	 Stack<String>                     AS_outlines;
		 Stack<RowContents>                AO_row_contents;
		 RowContents                       O_row_contents_1;
		 ArchPurposeContents               O_arch_purpose_contents_src, O_arch_purpose_contents_install, 
		                                   AO_arch_purpose_contents[];
		 
		 File           F_pnr_archive;
	     String         S_msg_1, S_msg_2, S_outline_c, S_outline_f, 
	                    S_version_current, S_hyperlink_txt, S_hyperlink_destination, S_pnr_archive, S_dnr_archive, S_prv_ver,
	                    S_cell_header;
	     StrBuilder     SB_outline_f;
	     boolean        B_msg_install;
	     int            i1, I_line_nbr_f1, I_nbr_lines_written_f1, I_min_lvl_package, I_min_lvl_category, I_dl_lvl_install, I_dl_lvl_src, I_pos_on_stack_f0;
	   
	    O_wb = new XSSFWorkbook();
	    O_font_bold = O_wb.createFont();
	    O_font_bold.setBold(true);
	    O_cell_style_bold = O_wb.createCellStyle();
	    O_cell_style_bold.setFont(O_font_bold);
	    
	    O_cell_style_bold_wrap = O_wb.createCellStyle();
	    O_cell_style_bold_wrap.setFont(O_font_bold);
	    O_cell_style_bold_wrap.setWrapText(true);
	    O_cell_style_bold_wrap.setVerticalAlignment(VerticalAlignment.TOP);
	    
	    O_creation_helper = O_wb.getCreationHelper();
	    O_color_blue      = new XSSFColor(Color.BLUE);
	    
	    O_font_hlink = O_wb.createFont();
	    O_font_hlink.setColor(O_color_blue);
	    O_font_hlink.setUnderline(FontUnderline.SINGLE);
	    O_font_hlink.setFontName("Courier New");
	    O_cell_style_hlink = O_wb.createCellStyle();
	    O_cell_style_hlink.setFont(O_font_hlink);
	    
	    try {
			O_work_sheet = O_wb.createSheet(S_work_sheet_name);
		} catch (IllegalArgumentException PI_E_ill_arg) {
			S_msg_1 = "Unable to insert work-sheet: \'" + S_work_sheet_name + "\' into workbook of type: \'" + O_wb.getClass().getName() + "\'";
			E_rt = new RuntimeException(S_msg_1, PI_E_ill_arg);
			throw E_rt;
		    }
	    
	    O_row = O_work_sheet.createRow(0);
  
	    for (i1 = 0; i1 < I_nbr_cols_f1; i1++) {
	    	O_cell = O_row.createCell(i1, CellType.STRING);
	    	O_cell.setCellStyle(O_cell_style_bold_wrap);
	    	S_cell_header = AS_header_row[i1];
	    	O_cell.setCellValue(S_cell_header);
	        }
	    O_work_sheet.createFreezePane(0, 1);  // freeze first row
		HAS_categories = PI_O_setup_ini_contents.HAS_categories;
//		AS_outlines     = new Stack<String>();
		AO_row_contents = new Stack<RowContents>();
		AS_categories  = HAS_categories.navigableKeySet();
		I_line_nbr_f1  = 0;
		I_nbr_lines_written_f1 = PB_I_nbr_lines_written.getValue();
		I_nbr_lines_written_f1++;
		
		for (String S_category: AS_categories) {
			I_min_lvl_category = Integer.MAX_VALUE;
			
//			AS_outlines.clear();
			AO_row_contents.clear();
			S_outline_c = "--- " + S_category + " ---"; 
			S_outline_f = "----------------------------- " + S_category + " -----------------------------";
			O_row_contents_1 = new RowContents(S_category);
			AO_row_contents.push(O_row_contents_1);
			 
			System.out.println(S_outline_c);
		//	PB_O_buff_wrtr.write(S_outline_f); PB_O_buff_wrtr.newLine();
//			AS_outlines.push(S_outline_f);
			AS_packages = HAS_categories.get(S_category);
			for (String S_package: AS_packages) {
				I_min_lvl_package = Integer.MAX_VALUE;
				SB_outline_f = new StrBuilder(S_package);
				
				I_pos_on_stack_f0 = PI_O_setup_ini_contents.HS_package_names.get(S_package).I_pos_on_stack_f0;
				O_pckg_info = PI_O_setup_ini_contents.AO_pckg_info.get(I_pos_on_stack_f0);
		 	    O_pckg_vers_info = O_pckg_info.O_version_current;
				if (O_pckg_vers_info == null) {
					O_pckg_vers_info = O_pckg_info.O_version_prev;
					if (O_pckg_vers_info == null) {
						S_msg_1 = "Object of type \'" + PckgVersionInfo.class.getName() + "\' must not be null here.";
						E_np = new NullPointerException(S_msg_1);
						S_msg_2 = "Unable to obtain download-level of package/module: \'" + S_category + "/" + S_package + "\'";
						E_rt = new RuntimeException(S_msg_2, E_np);
						throw E_rt;
					    }
				    }
			     
				 B_msg_install                   = false;
				 O_arch_purpose_contents_install = null;
				
				 S_version_current = O_pckg_vers_info.S_version;
				 SB_outline_f.append("(" + S_version_current + "):");
				 O_arch_info_install = O_pckg_vers_info.O_install;
				 S_hyperlink_txt = null;
				 if (O_arch_info_install != null) {
					 I_dl_lvl_install  = O_arch_info_install.E_dl_status.ordinal();
					 if (I_dl_lvl_install < I_min_lvl_package) {
						 I_min_lvl_package = I_dl_lvl_install; 
					     }
					 if (I_dl_lvl_install < DL_MINIMUM_REQUIREMENT) {
		  			    SB_outline_f.append(" install");
						if (I_dl_lvl_install == ArchInfo.I_dl_status_prev) {
							SB_outline_f.append("(" + O_arch_info_install.S_ver_locally_stored + ")");
							S_prv_ver = O_arch_info_install.S_ver_locally_stored;
						    }
						else {
							SB_outline_f.append(" " + O_arch_info_install.E_dl_status.name());
							S_prv_ver = null;
						    }
						S_pnr_archive = O_pckg_vers_info.O_install.S_pnr_archive;
					    F_pnr_archive = new File(S_pnr_archive);
					    S_dnr_archive = F_pnr_archive.getParent();
					     
					     S_hyperlink_txt         = S_dnr_archive + "\\";
					     S_hyperlink_destination = S_dna_cygw_repository_root + "/" + PI_S_dnr_site + "/" + S_dnr_archive;
					     O_arch_purpose_contents_install = new ArchPurposeContents(
					    		    O_arch_info_install.E_dl_status, 
					    		    S_prv_ver, 
					    		    S_hyperlink_txt,
					    		    S_hyperlink_destination);
						B_msg_install = true;
					    } //  (I_dl_lvl_install < DL_MINIMUM_REQUIREMENT)
				     }
				     
				 O_arch_info_src             = O_pckg_vers_info.O_src;
				 O_arch_purpose_contents_src = null;
				 
				 if (O_arch_info_src != null) {
					 I_dl_lvl_src     = O_arch_info_src.E_dl_status.ordinal();
					 if (I_dl_lvl_src < I_min_lvl_package) {
						 I_min_lvl_package = I_dl_lvl_src; 
					     }
					 if (I_dl_lvl_src < DL_MINIMUM_REQUIREMENT) {
						if (B_msg_install) {
							SB_outline_f.append(" ---");
						    }
						SB_outline_f.append(" src");
						if (I_dl_lvl_src == ArchInfo.I_dl_status_prev) {
							SB_outline_f.append("(" + O_arch_info_src.S_ver_locally_stored + ")");
							S_prv_ver = O_arch_info_src.S_ver_locally_stored;
						    }
						else {
							SB_outline_f.append(" " + O_arch_info_src.E_dl_status.name());
							S_prv_ver = null;
						   }
						S_pnr_archive = O_pckg_vers_info.O_src.S_pnr_archive;
					    F_pnr_archive = new File(S_pnr_archive);
					    S_dnr_archive = F_pnr_archive.getParent();
					     
					    S_hyperlink_txt         = S_dnr_archive + "\\";
					    S_hyperlink_destination = S_dna_cygw_repository_root + "/" + PI_S_dnr_site + "/" + S_dnr_archive;
					    O_arch_purpose_contents_src = new ArchPurposeContents(
					    		    O_arch_info_src.E_dl_status, 
					    		    S_prv_ver, 
					    		    S_hyperlink_txt,
					    		    S_hyperlink_destination);
						}
				    }	 
				 if (I_min_lvl_package < DL_MINIMUM_REQUIREMENT) {
					 S_outline_f = SB_outline_f.toString();
//					 AS_outlines.push(S_outline_f);
					 AO_arch_purpose_contents = new ArchPurposeContents[] {O_arch_purpose_contents_install, O_arch_purpose_contents_src};
					 O_row_contents_1 = new RowContents(S_package, S_version_current, AO_arch_purpose_contents);
					 AO_row_contents.push(O_row_contents_1);
				     }
				 if (I_min_lvl_package < I_min_lvl_category) {
					 I_min_lvl_category = I_min_lvl_package;
				     }
			} // END loop packages
			if (I_min_lvl_category < DL_MINIMUM_REQUIREMENT) { 
			   for (RowContents O_row_contents_2: AO_row_contents) {
					O_row = O_work_sheet.createRow(I_nbr_lines_written_f1);  // rows are 0 based				
					FV_write_row(O_row, O_row_contents_2);
					I_nbr_lines_written_f1++;
				    }
			   }
		}  // END loop categories
	    PB_I_nbr_lines_written.setValue(I_nbr_lines_written_f1);
	
	    
    try {
		O_wb.write(PB_O_fs_output);
	} catch (IOException | OpenXML4JRuntimeException PI_E_io) {
		S_msg_1 = "Unable to write back " + I_nbr_lines_written_f1 + " rows to Workbook of type: \'" + O_wb.getClass().getName() +  "\' ";
		E_rt = new RuntimeException(S_msg_1, PI_E_io);
		throw E_rt;
	    }    
	try {    
	    O_wb.close();
	} catch (IOException PI_E_io) {
		S_msg_1 = "Unable to close workbook of type: \'" + O_wb.getClass().getName() +  "\' after writing";
		E_rt = new RuntimeException(S_msg_1, PI_E_io);
		throw E_rt;
	    }
	}
}