/*
 * 
 *
 * Created on March 25, 2004, 6:11 PM
 */

package com.metavize.tran.virus.gui;

import com.metavize.gui.transform.*;
import com.metavize.gui.pipeline.MPipelineJPanel;
import com.metavize.gui.widgets.editTable.*;
import com.metavize.gui.util.*;

import com.metavize.mvvm.*;
import com.metavize.mvvm.tran.*;
import com.metavize.tran.virus.*;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.*;
import java.util.Vector;
import javax.swing.event.*;

public class GeneralConfigJPanel extends MEditTableJPanel {

    public GeneralConfigJPanel() {
        super(true, true);
        super.setInsets(new Insets(4, 4, 2, 2));
        super.setTableTitle("General Settings");
        super.setDetailsTitle("rule notes");
        super.setAddRemoveEnabled(false);
        
        // create actual table model
        GeneralTableModel tableModel = new GeneralTableModel();
        this.setTableModel( tableModel );
    }
}


class GeneralTableModel extends MSortedTableModel{ 

    private static final int T_TW = Util.TABLE_TOTAL_WIDTH;
    private static final int C0_MW = Util.STATUS_MIN_WIDTH; /* status */
    private static final int C1_MW = Util.LINENO_MIN_WIDTH; /* # - invisible */
    private static final int C2_MW = 200; /* setting name */
    private static final int C3_MW = 200; /* setting value */
    private static final int C4_MW = Util.chooseMax(T_TW - (C1_MW + C2_MW + C3_MW), 120); /* description */

    
    public TableColumnModel getTableColumnModel(){
        
        DefaultTableColumnModel tableColumnModel = new DefaultTableColumnModel();
        //                                 #  min    rsz    edit   remv   desc   typ            def
        addTableColumn( tableColumnModel,  0, C0_MW, false, false, false, false, String.class,  null, sc.TITLE_STATUS );
        addTableColumn( tableColumnModel,  1, C1_MW, false, false, true,  false, Integer.class, null, sc.TITLE_INDEX );
        addTableColumn( tableColumnModel,  2, C2_MW, true,  false, false, false, String.class,  null, "setting name");
        addTableColumn( tableColumnModel,  3, C3_MW, true,  true,  false, false, Object.class,  null, sc.bold("setting value"));
        addTableColumn( tableColumnModel,  4, C4_MW, true,  false, true,  true,  String.class,  sc.EMPTY_DESCRIPTION, sc.TITLE_DESCRIPTION );
        return tableColumnModel;
    }

    public void generateSettings(Object settings, boolean validateOnly){
        Vector tempRowVector;
	
        // ftpDisableResume
        tempRowVector = (Vector) dataVector.elementAt(0);
	boolean ftpDisableResume = (Boolean) tempRowVector.elementAt(3);
	String ftpDisableResumeDetails = (String) tempRowVector.elementAt(4);
        
        // httpDisableResume
        tempRowVector = (Vector) dataVector.elementAt(1);
	boolean httpDisableResume = (Boolean) tempRowVector.elementAt(3);
	String httpDisableResumeDetails = (String) tempRowVector.elementAt(4);

        // tricklePercent
        tempRowVector = (Vector) dataVector.elementAt(2);
	int tricklePercent = ((Integer)((SpinnerNumberModel)tempRowVector.elementAt(3)).getValue()).intValue();
	String tricklePercentDetails = (String) tempRowVector.elementAt(4);

	// SAVE SETTINGS //////////
	if( !validateOnly ){
	    VirusSettings virusSettings = (VirusSettings) settings;
	    virusSettings.setFtpDisableResume( ftpDisableResume );
	    virusSettings.setFtpDisableResumeDetails( ftpDisableResumeDetails );
	    virusSettings.setHttpDisableResume( httpDisableResume );
	    virusSettings.setHttpDisableResumeDetails( httpDisableResumeDetails );
	    virusSettings.setTricklePercent( tricklePercent );
	    virusSettings.setTricklePercentDetails( tricklePercentDetails );
	}

    }
    
    public Vector generateRows(Object settings){
	VirusSettings virusSettings = (VirusSettings) settings;
        Vector allRows = new Vector(8);
        Vector tempRowVector;

        // ftpDisableResume
        tempRowVector = new Vector(4);
        tempRowVector.add(super.ROW_SAVED);
        tempRowVector.add(new Integer(1));
        tempRowVector.add("disable FTP download resume");
        tempRowVector.add( virusSettings.getFtpDisableResume() );
        tempRowVector.add( "This setting specifies that if an FTP transfer has stopped or been blocked for some reason (perhaps a virus was detected), the transfer cannot be restarted from the middle where it was left off.  Allowing transfers to restart from the middle may allow unwanted traffic to enter the network." ); //virusSettings.getFtpDisableResumeDetails() );
        allRows.add( tempRowVector );
        
        // httpDisableResume
        tempRowVector = new Vector(4);
        tempRowVector.add(super.ROW_SAVED);
        tempRowVector.add(new Integer(2));
        tempRowVector.add("disable HTTP download resume");
        tempRowVector.add( virusSettings.getHttpDisableResume() );
        tempRowVector.add( "This setting specifies that if an HTTP transfer has stopped or been blocked for some reason (perhaps a virus was detected), the transfer cannot be restarted from the middle where it was left off.  Allowing transfers to restart from the middle may allow unwanted traffic to enter the network." ); //virusSettings.getHttpDisableResumeDetails() );
        allRows.add( tempRowVector );
        
        // tricklePercent
        tempRowVector = new Vector(4);
        tempRowVector.add(super.ROW_SAVED);
        tempRowVector.add(new Integer(3));
        tempRowVector.add("scan trickle rate (percent)");
        tempRowVector.add( new SpinnerNumberModel( virusSettings.getTricklePercent(), 1, 99, 1) );
        tempRowVector.add( "This setting specifies the rate the user will download a file (which is being scanned), relative to the rate the EdgeGuard is receiving the actual file." ); //virusSettings.getTricklePercentDetails() );
        allRows.add( tempRowVector );

        return allRows;
    }
}
