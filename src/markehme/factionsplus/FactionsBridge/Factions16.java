package markehme.factionsplus.FactionsBridge;

import java.io.ObjectInputStream.*;
import java.lang.reflect.*;
import java.util.*;

import markehme.factionsplus.*;
import markehme.factionsplus.extras.*;
import markehme.factionsplus.util.*;

import com.massivecraft.factions.*;
import com.massivecraft.factions.cmd.*;



@SuppressWarnings( "unused" )
public class Factions16 extends FactionsBase implements FactionsAny {
	
	private Method							mSetPeaceful		= null;
	private Method							mIsPeaceful		= null;
	private Method							methodUpdateHelp	= null;
	private Object							instanceOfCmdHelp	= null;
	private Field							fieldCmdHelp		= null;
	private Object							instField			= null;
	private Field							fHelpPages			= null;
	private ArrayList<ArrayList<String>>	instanceOfHelpPages	= null;
	private Method 							mSetChatMode		= null;
	private Method 							mGetChatMode		= null;
	
	//maps Factions 1.6 com.massivecraft.factions.struct.ChatMode  to FactionsAny.ChatMode
	private TwoWayMapOfNonNulls<Object, FactionsAny.ChatMode>	mapChatMode		= new TwoWayMapOfNonNulls<Object, FactionsAny.ChatMode>();
	
	protected Factions16( ) {
		super();
		
		boolean failed = false;
		
		try {
			mSetPeaceful = Faction.class.getMethod( "setPeaceful", boolean.class );
			
			mIsPeaceful=Faction.class.getMethod("isPeaceful");
			
			Class clas = Class.forName( "com.massivecraft.factions.cmd.CmdHelp" );
			
			methodUpdateHelp = clas.getMethod( "updateHelp" );
			
			Class fcmdroot = Class.forName( "com.massivecraft.factions.cmd.FCmdRoot" );
			
			fieldCmdHelp = fcmdroot.getField( "cmdHelp" );
			
			fHelpPages = clas.getField( "helpPages" );
			
			Class classChatMode=Class.forName("com.massivecraft.factions.struct.ChatMode");
			Reflective.mapEnumsToSome( mapChatMode, classChatMode, FactionsAny.ChatMode.class,null);
			
			Class classFPlayer= Class.forName( "com.massivecraft.factions.FPlayer" );
			mSetChatMode=classFPlayer.getMethod("setChatMode", classChatMode);
			mGetChatMode=classFPlayer.getMethod("getChatMode");
			
		} catch ( NoSuchMethodException e ) {// multi catch could've worked but unsure if using jdk7 to compile
			e.printStackTrace();
			failed = true;
		} catch ( SecurityException e ) {
			e.printStackTrace();
			failed = true;
		} catch ( ClassNotFoundException e ) {
			e.printStackTrace();
			failed = true;
		} catch ( NoSuchFieldException e ) {
			e.printStackTrace();
			failed = true;
		} finally {
			if ( failed ) {
				throw FactionsPlusPlugin.bailOut( "failed to hook into Factions 1.6.x" );
			}
		}
	}
	
	
	@Override
	public void setFlag( Faction forFaction, FactionsAny.FFlag whichFlag, Boolean whatState ) {
		assert null != forFaction;
		assert null != whichFlag;
		assert null != whatState;
		
		boolean failed = false;
		try {
			switch ( whichFlag ) {
			case PEACEFUL:
				mSetPeaceful.invoke( forFaction, whatState );
				break;
			// TODO: add all flags here, those from FactionsAny.FFlag
				//or make a mapping between the methods and the flags, clearly. 
			default:
				throw FactionsPlusPlugin.bailOut( "setFlag, plugin author forgot to define a case to handle this flag: "
					+ whichFlag );
				// or forgot to put a "break;"
			}
			
		} catch ( IllegalAccessException e ) {
			e.printStackTrace();
			failed = true;
		} catch ( IllegalArgumentException e ) {
			e.printStackTrace();
			failed = true;
		} catch ( InvocationTargetException e ) {
			e.printStackTrace();
			failed = true;
		} finally {
			if ( failed ) {
				throw FactionsPlusPlugin.bailOut( "failed to invoke " + mSetPeaceful );
			}
		}
	}
	
	
	@Override
	public boolean getFlag( Faction forFaction, FactionsAny.FFlag whichFlag ) {
		assert null != forFaction;
		assert null != whichFlag;
		
		Throwable err=null;
		try {
			
			switch ( whichFlag ) {
			case PEACEFUL:
				Object ret = mIsPeaceful.invoke( forFaction );
				assert null != ret;
				assert ret instanceof Boolean;
				return ((Boolean)ret).booleanValue();
//				break;
			case POWERLOSS:
				if (Utilities.isWarZone( forFaction )) {
					//warzone always overrides the nopowerlossworlds list even if that is not even considered here
					//XXX: if you see compile error here, please use Factions.jar for version 1.6.x instead of 1.7.x (or github branch 1.6.x not master)
					//the .jar will work with 1.7.x version of Faction, once it's compiled anyway.
					return Conf.warZonePowerLoss;
				}
				// not warzone
				if (Conf.wildernessPowerLoss && Utilities.isWilderness( forFaction )  ) {
					return true;
				}

				if (!Conf.peacefulMembersDisablePowerLoss && Utilities.isPeaceful( forFaction ) 
						&& !Utilities.isWilderness( forFaction )
						&& !Utilities.isSafeZone( forFaction )) {
					return true;
				}
				
				return false;
			default:
				throw FactionsPlusPlugin.bailOut( "getFlag, plugin author forgot to define a case to handle this flag: "
					+ whichFlag );
				// or forgot to put a "break;"
			}
			
			
		} catch ( IllegalArgumentException e ) {
			err=e;
		} catch ( IllegalAccessException e ) {
			err=e;
		} catch ( InvocationTargetException e ) {
			err=e;
		} finally {
			if ( null != err ) {
				throw FactionsPlusPlugin.bailOut( err, "failed in getFlag");
			}
		}
//		return false;
		throw null;//should not be reached
	}

	
	
	private final static byte	howManyPerPage	= 5;
	private byte				currentPerPage	= 0;
	private ArrayList<String>	pageLines		= null;
	
	
	@Override
	public void addSubCommand( FCommand base, FCommand subCommand ) {
		super.addSubCommand( base, subCommand );
		// for 1.6 need to add the command to help manually
		if ( null == instanceOfCmdHelp ) {
			Throwable failed = null;
			try {
				// lazy init this(one time since plugin.onEnable()), cause on .init() was probably too soon
				instanceOfCmdHelp = fieldCmdHelp.get( P.p.cmdBase );//this is good here, cmdBase!
				methodUpdateHelp.invoke( instanceOfCmdHelp );// P.p.cmdBase.cmdHelp.updateHelp();
				instanceOfHelpPages = (ArrayList<ArrayList<String>>)fHelpPages.get( instanceOfCmdHelp );
			} catch ( IllegalAccessException e ) {
				failed = e;
			} catch ( IllegalArgumentException e ) {
				failed = e;
			} catch ( InvocationTargetException e ) {
				failed = e;
			} finally {
				if ( null != failed ) {
					throw FactionsPlusPlugin.bailOut(failed, "failed to invoke " + methodUpdateHelp );
				}
			}
		}
		
		
		if ( null == pageLines ) {
			pageLines = new ArrayList<String>();
			currentPerPage = 0;
		}
		
		pageLines.add( subCommand.getUseageTemplate( true ) );
		if ( currentPerPage >= howManyPerPage ) {
			instanceOfHelpPages.add( pageLines );
			pageLines = null;
		} else {
			currentPerPage++;
		}
	}
	
	
	@Override
	public final void finalizeHelp() {
		if ( null == instanceOfHelpPages ) {
			throw FactionsPlusPlugin.bailOut( "this should not happen, bad call order" );
		} else {
			if ( null != pageLines ) {
				instanceOfHelpPages.add( pageLines );
				pageLines = null;
			}
			currentPerPage = 0;
		}
	}


	@Override
	public FactionsAny.ChatMode setChatMode(FPlayer forWhatPlayer, FactionsAny.ChatMode chatMode ) {
		boolean failed=false;
		FactionsAny.ChatMode ret=null;
		try {
			Object factionsChatMode_Enum = mapChatMode.getLeftSide( chatMode );
			if ( null == factionsChatMode_Enum ) {
				failed = true;
				throw FactionsPlusPlugin.bailOut( "would never be null if .init() above failed to properly map ...");
			} else {
				ret=getChatMode( forWhatPlayer );
				mSetChatMode.invoke( forWhatPlayer, factionsChatMode_Enum );
			}
		} catch ( IllegalAccessException e ) {
			e.printStackTrace();
			failed=true;
		} catch ( IllegalArgumentException e ) {
			e.printStackTrace();
			failed=true;
		} catch ( InvocationTargetException e ) {
			e.printStackTrace();
			failed=true;
		}finally {
			if ( failed ) {
				throw FactionsPlusPlugin.bailOut( "failed to invoke " + mSetChatMode );
			}
			if (null == ret) {
				throw FactionsPlusPlugin.bailOut( "failure within the code logic");
			}
		}
		return ret;//even if there was actually no chatMode change when compared to the previous, true means it 
	}


	@Override
	public ChatMode getChatMode(FPlayer forWhatPlayer) {
		boolean failed=false;
		try {
			Object factionsEnum = mGetChatMode.invoke(forWhatPlayer);
			if ( null == factionsEnum ) {
				failed = true;
			} else {
				ChatMode cm = mapChatMode.getRightSide( factionsEnum );
				if ( null == cm ) {
					failed = true;
				}
				return cm;
			}
		} catch ( IllegalAccessException e ) {
			e.printStackTrace();
			failed=true;
		} catch ( IllegalArgumentException e ) {
			e.printStackTrace();
			failed=true;
		} catch ( InvocationTargetException e ) {
			e.printStackTrace();
			failed=true;
		}finally {
			if ( failed ) {
				throw FactionsPlusPlugin.bailOut( "failed to invoke " + mGetChatMode );
			}
		}
		throw null;//not reached!
	}


	@Override
	public final boolean isFactions17() {
		return false;
	}


}
