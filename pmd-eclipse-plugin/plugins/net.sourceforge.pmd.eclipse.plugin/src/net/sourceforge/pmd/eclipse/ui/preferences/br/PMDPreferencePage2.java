package net.sourceforge.pmd.eclipse.ui.preferences.br;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.pmd.PropertyDescriptor;
import net.sourceforge.pmd.Rule;
import net.sourceforge.pmd.RuleSet;
import net.sourceforge.pmd.eclipse.plugin.PMDPlugin;
import net.sourceforge.pmd.eclipse.runtime.preferences.impl.PreferenceUIStore;
import net.sourceforge.pmd.eclipse.ui.nls.StringKeys;
import net.sourceforge.pmd.eclipse.ui.preferences.editors.SWTUtil;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.DescriptionPanelManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.ExamplePanelManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.ExclusionPanelManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.PerRulePropertyPanelManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.QuickFixPanelManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.RulePropertyManager;
import net.sourceforge.pmd.eclipse.ui.preferences.panelmanagers.XPathPanelManager;
import net.sourceforge.pmd.eclipse.util.Util;

import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Tree;

public class PMDPreferencePage2 extends AbstractPMDPreferencePage implements RuleSelectionListener, ModifyListener, ValueChangeListener {

	private TabFolder 		     	tabFolder;
	private RulePropertyManager[]   rulePropertyManagers;
	private RuleTableManager		tableManager;
	
	// columns shown in the rule treetable in the desired order
	private static final RuleColumnDescriptor[] availableColumns = new RuleColumnDescriptor[] {
		TextColumnDescriptor.name,
		//TextColumnDescriptor.priorityName,
		IconColumnDescriptor.priority,
		TextColumnDescriptor.fixCount,
		TextColumnDescriptor.since,
		TextColumnDescriptor.ruleSetName,
		TextColumnDescriptor.ruleType,
		TextColumnDescriptor.minLangVers,		
		TextColumnDescriptor.language,
		ImageColumnDescriptor.filterViolationRegex,    // regex text -> compact color squares (for comparison)
		ImageColumnDescriptor.filterViolationXPath,    // xpath text -> compact color circles (for comparison)
		TextColumnDescriptor.properties,
		};

	// last item in this list is the grouping used at startup
	private static final Object[][] groupingChoices = new Object[][] {
		{ TextColumnDescriptor.ruleSetName,       StringKeys.MSGKEY_PREF_RULESET_COLUMN_RULESET},   
		{ TextColumnDescriptor.since,             StringKeys.MSGKEY_PREF_RULESET_GROUPING_PMD_VERSION },
		{ TextColumnDescriptor.priorityName,      StringKeys.MSGKEY_PREF_RULESET_COLUMN_PRIORITY },
		{ TextColumnDescriptor.ruleType,          StringKeys.MSGKEY_PREF_RULESET_COLUMN_RULE_TYPE },
		{ TextColumnDescriptor.language,		  StringKeys.MSGKEY_PREF_RULESET_COLUMN_LANGUAGE },
        { ImageColumnDescriptor.filterViolationRegex, StringKeys.MSGKEY_PREF_RULESET_GROUPING_REGEX },
		{ null, 								  StringKeys.MSGKEY_PREF_RULESET_GROUPING_NONE }
		};
	
	private static final Map<Class<?>, ValueFormatter> formattersByType = new HashMap<Class<?>, ValueFormatter>();

	static {   // used to render property values in short form in main table
	    formattersByType.put(String.class,      ValueFormatter.StringFormatter);
        formattersByType.put(String[].class,    ValueFormatter.MultiStringFormatter);
        formattersByType.put(Boolean.class,     ValueFormatter.BooleanFormatter);
        formattersByType.put(Boolean[].class,   ValueFormatter.ObjectArrayFormatter);
        formattersByType.put(Integer.class,     ValueFormatter.NumberFormatter);
        formattersByType.put(Integer[].class,   ValueFormatter.ObjectArrayFormatter);
        formattersByType.put(Long.class,        ValueFormatter.NumberFormatter);
        formattersByType.put(Long[].class,      ValueFormatter.ObjectArrayFormatter);
        formattersByType.put(Float.class,       ValueFormatter.NumberFormatter);
        formattersByType.put(Float[].class,     ValueFormatter.ObjectArrayFormatter);
        formattersByType.put(Double.class,      ValueFormatter.NumberFormatter);
        formattersByType.put(Double[].class,    ValueFormatter.ObjectArrayFormatter);
        formattersByType.put(Character.class,   ValueFormatter.ObjectFormatter);
        formattersByType.put(Character[].class, ValueFormatter.ObjectArrayFormatter);
        formattersByType.put(Class.class,       ValueFormatter.TypeFormatter);
        formattersByType.put(Class[].class,     ValueFormatter.MultiTypeFormatter);        
        formattersByType.put(Method.class,      ValueFormatter.MethodFormatter);
        formattersByType.put(Method[].class,    ValueFormatter.MultiMethodFormatter);
        formattersByType.put(Object[].class,    ValueFormatter.ObjectArrayFormatter);
	}
	
	public PMDPreferencePage2() {
		
	}
	
	protected String descriptionId() {
		return StringKeys.MSGKEY_PREF_RULESET_TITLE;
	}
	
	@Override
	protected Control createContents(Composite parent) {
 
		tableManager = new RuleTableManager(availableColumns, formattersByType, PMDPlugin.getDefault().loadPreferences());
		tableManager.modifyListener(this);
		tableManager.selectionListener(this);
		
	    populateRuleset();   
	    
		Composite composite = new Composite(parent, SWT.NULL);
		layoutControls(composite);
		
		tableManager.populateRuleTable();
		int i =  PreferenceUIStore.instance.selectedPropertyTab() ;
		tabFolder.setSelection( i );
		
		return composite;
	}
    
	public void createControl(Composite parent) {
		super.createControl(parent);
		
		setModified(false);		
	}
	/**
	 * Create buttons for rule properties table management
	 * @param parent Composite
	 * @return Composite
	 */
	private Composite buildRulePropertiesTableButtons(Composite parent) {
		Composite composite = new Composite(parent, SWT.NULL);
		RowLayout rowLayout = new RowLayout();
		rowLayout.type = SWT.VERTICAL;
		rowLayout.wrap = false;
		rowLayout.pack = false;
		composite.setLayout(rowLayout);

		return composite;
	}
	
	private Composite createRuleSection(Composite parent) {
	    
	    Composite ruleSection = new Composite(parent, SWT.NULL);
	    
	    // Create the controls (order is important !)
        Composite groupCombo = tableManager.buildGroupCombo(ruleSection, StringKeys.MSGKEY_PREF_RULESET_RULES_GROUPED_BY, groupingChoices);
	    
	    Tree ruleTree = tableManager.buildRuleTreeViewer(ruleSection);
	    tableManager.groupBy(null);
	    
        Composite ruleTableButtons = tableManager.buildRuleTableButtons(ruleSection);
        Composite rulePropertiesTableButtons = buildRulePropertiesTableButtons(ruleSection);

        // Place controls on the layout
        GridLayout gridLayout = new GridLayout(3, false);
        ruleSection.setLayout(gridLayout);

        GridData data = new GridData();
        data.horizontalSpan = 3;
        groupCombo.setLayoutData(data);

        data = new GridData();
        data.heightHint = 200;                          data.widthHint = 350;
        data.horizontalSpan = 1;
        data.horizontalAlignment = GridData.FILL;       data.verticalAlignment = GridData.FILL;
        data.grabExcessHorizontalSpace = true;          data.grabExcessVerticalSpace = true;
        ruleTree.setLayoutData(data);

        data = new GridData();
        data.horizontalSpan = 1;
        data.horizontalAlignment = GridData.FILL;       data.verticalAlignment = GridData.FILL;
        ruleTableButtons.setLayoutData(data);

        data = new GridData();
        data.horizontalSpan = 1;
        data.horizontalAlignment = GridData.FILL;       data.verticalAlignment = GridData.FILL;
        rulePropertiesTableButtons.setLayoutData(data);
        
        return ruleSection;
	}
	
	/**
	 * Method buildTabFolder.
	 * @param parent Composite
	 * @return TabFolder
	 */
	private TabFolder buildTabFolder(Composite parent) {

		tabFolder = new TabFolder(parent, SWT.TOP);

		rulePropertyManagers = new RulePropertyManager[] {
		    buildPropertyTab(tabFolder,    0, SWTUtil.stringFor(StringKeys.MSGKEY_PREF_RULESET_TAB_PROPERTIES)),
		    buildDescriptionTab(tabFolder, 1, SWTUtil.stringFor(StringKeys.MSGKEY_PREF_RULESET_TAB_DESCRIPTION)),
		    buildUsageTab(tabFolder,       2, SWTUtil.stringFor(StringKeys.MSGKEY_PREF_RULESET_TAB_FILTERS)),
		    buildXPathTab(tabFolder,       3, SWTUtil.stringFor(StringKeys.MSGKEY_PREF_RULESET_TAB_XPATH)),   
		    buildQuickFixTab(tabFolder,    4, SWTUtil.stringFor(StringKeys.MSGKEY_PREF_RULESET_TAB_FIXES)), 
		    buildExampleTab(tabFolder,     5, SWTUtil.stringFor(StringKeys.MSGKEY_PREF_RULESET_TAB_EXAMPLES)),
		    };

		tabFolder.pack();
		return tabFolder;
	}

	/**
	 * @param parent TabFolder
	 * @param index int
	 */
	private RulePropertyManager buildPropertyTab(TabFolder parent, int index, String title) {

	    TabItem tab = new TabItem(parent, 0, index);
	    tab.setText(title);

		PerRulePropertyPanelManager manager = new PerRulePropertyPanelManager(this);
		tab.setControl(
		    manager.setupOn(parent, this)
		    );
		manager.tab(tab);
		return manager;
	}

	/**
	 * @param parent TabFolder
	 * @param index int
	 */
	private RulePropertyManager buildDescriptionTab(TabFolder parent, int index, String title) {

		TabItem tab = new TabItem(parent, 0, index);
		tab.setText(title);

        DescriptionPanelManager manager = new DescriptionPanelManager(this);
        tab.setControl(
            manager.setupOn(parent)
            );
        manager.tab(tab);
        return manager;
	}

    /**
     * @param parent TabFolder
     * @param index int
     */
    private RulePropertyManager buildXPathTab(TabFolder parent, int index, String title) {

        TabItem tab = new TabItem(parent, 0, index);
        tab.setText(title);

        XPathPanelManager manager = new XPathPanelManager(this);
        tab.setControl(
            manager.setupOn(parent)
            );
        manager.tab(tab);
        return manager;
    }
	
	/**
     * @param parent TabFolder
     * @param index int
     */
    private RulePropertyManager buildExampleTab(TabFolder parent, int index, String title) {

        TabItem tab = new TabItem(parent, 0, index);
        tab.setText(title);

        ExamplePanelManager manager = new ExamplePanelManager(this);
        tab.setControl(
            manager.setupOn(parent)
            );
        manager.tab(tab);
        return manager;
    }    
    
    /**
     * @param parent TabFolder
     * @param index int
     */
    private RulePropertyManager buildQuickFixTab(TabFolder parent, int index, String title) {

        TabItem tab = new TabItem(parent, 0, index);
        tab.setText(title);

        QuickFixPanelManager manager = new QuickFixPanelManager(this);
        tab.setControl(
            manager.setupOn(parent)
            );
        manager.tab(tab);
        return manager;
    }
    
	/**
	 *
	 * @param parent TabFolder
	 * @param index int
	 * @param title String
	 */
	private RulePropertyManager buildUsageTab(TabFolder parent, int index, String title) {

		TabItem tab = new TabItem(parent, 0, index);
		tab.setText(title);

		ExclusionPanelManager manager = new ExclusionPanelManager(this);
		tab.setControl(
			manager.setupOn(
					parent,
					SWTUtil.stringFor(StringKeys.MSGKEY_LABEL_EXCLUSION_REGEX),
					SWTUtil.stringFor(StringKeys.MSGKEY_LABEL_XPATH_EXCLUSION),
					SWTUtil.stringFor(StringKeys.MSGKEY_LABEL_COLOUR_CODE)
					)
			);
		manager.tab(tab);
		return manager;
	}
	
	public void changed(Rule rule, PropertyDescriptor<?> desc, Object newValue) {
	        // TODO enhance to recognize default values
	     setModified();                
	     tableManager.updated(rule);
	}
		
	public void changed(RuleSelection selection, PropertyDescriptor<?> desc, Object newValue) {
			// TODO enhance to recognize default values
				
		for (Rule rule : selection.allRules()) {
			if (newValue != null) {		// non-reliable update behaviour, alternate trigger option - weird
	//				ruleTreeViewer.getTree().redraw();
			//		System.out.println("doing redraw");
			} else {
	//				ruleTreeViewer.update(rule, null);
			//		System.out.println("viewer update");
			}
		}
		for (RulePropertyManager manager : rulePropertyManagers) {
		    manager.validate();
		}
		
		setModified();
	}

	/**
     * Main layout
     * @param parent Composite
     */
    private void layoutControls(Composite parent) {

        parent.setLayout(new FormLayout());
        int ruleTableFraction = 55;	//PreferenceUIStore.instance.tableFraction();
        
        // Create the sash first, so the other controls can be attached to it.
        final Sash sash = new Sash(parent, SWT.HORIZONTAL);
        FormData data = new FormData();
        data.left = new FormAttachment(0, 0);                   // attach to left
        data.right = new FormAttachment(100, 0);                // attach to right
        data.top = new FormAttachment(ruleTableFraction, 0);
        sash.setLayoutData(data);
        sash.addSelectionListener(new SelectionAdapter() {
          public void widgetSelected(SelectionEvent event) {
            // Re-attach to the top edge, and we use the y value of the event to determine the offset from the top
            ((FormData)sash.getLayoutData()).top = new FormAttachment(0, event.y);
//            PreferenceUIStore.instance.tableFraction(event.y);
            sash.getParent().layout();
          }
        });

        // Create the first text box and attach its bottom edge to the sash
        Composite ruleSection = createRuleSection(parent);
        data = new FormData();
        data.top = new FormAttachment(0, 0);
        data.bottom = new FormAttachment(sash, 0);
        data.left = new FormAttachment(0, 0);
        data.right = new FormAttachment(100, 0);
        ruleSection.setLayoutData(data);

        // Create the second text box and attach its top edge to the sash
        TabFolder propertySection = buildTabFolder(parent);
        data = new FormData();
        data.top = new FormAttachment(sash, 0);
        data.bottom = new FormAttachment(100, 0);
        data.left = new FormAttachment(0, 0);
        data.right = new FormAttachment(100, 0);
        propertySection.setLayoutData(data);
    }

	/**
	 * @see org.eclipse.jface.preference.IPreferencePage#performOk()
	 */
	@Override
    public boolean performOk() {
		
		saveUIState();
		
		if (isModified()) {
			updateRuleSet();
			rebuildProjects();
			storeActiveRules();
		}

		return super.performOk();
	}
	
	@Override
	public boolean performCancel() {

		saveUIState();		
		return super.performCancel();
	}
	
	/**
	 * @see org.eclipse.jface.preference.PreferencePage#performDefaults()
	 */
	@Override
    protected void performDefaults() {
		tableManager.populateRuleTable();
		super.performDefaults();
	}
	
	private void populateRuleset() {
	    
	    RuleSet defaultRuleSet = plugin.getPreferencesManager().getRuleSet();
        RuleSet ruleSet = new RuleSet();
        ruleSet.addRuleSet(defaultRuleSet);
        ruleSet.setName(defaultRuleSet.getName());
        ruleSet.setDescription(Util.asCleanString(defaultRuleSet.getDescription()));
        ruleSet.addExcludePatterns(defaultRuleSet.getExcludePatterns());
        ruleSet.addIncludePatterns(defaultRuleSet.getIncludePatterns());
        
        tableManager.useRuleSet(ruleSet);
	}
	
	public void selection(RuleSelection selection) {
		   
		for (RulePropertyManager manager : rulePropertyManagers) {
			manager.manage(selection);
		    manager.validate();
		}
	}
	
	/**
	 * If user wants to, rebuild all projects
	 */
	private void rebuildProjects() {
		if (MessageDialog.openQuestion(getShell(), getMessage(StringKeys.MSGKEY_QUESTION_TITLE),
				getMessage(StringKeys.MSGKEY_QUESTION_RULES_CHANGED))) {
			try {
				ProgressMonitorDialog monitorDialog = new ProgressMonitorDialog(getShell());
				monitorDialog.run(true, true, new IRunnableWithProgress() {
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						try {
							ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, monitor);
						} catch (CoreException e) {
							plugin.logError("Exception building all projects after a preference change", e);
						}
					}
				});
			} catch (Exception e) {
				plugin.logError("Exception building all projects after a preference change", e);
			}
		}
	}
	
	private void saveUIState() {
		tableManager.saveUIState();
		int i =  tabFolder.getSelectionIndex();
		PreferenceUIStore.instance.selectedPropertyTab( i );
		PreferenceUIStore.instance.save();
	}
	

	private void storeActiveRules() {		
		
		List<Rule> chosenRules = tableManager.activeRules();
		for (Rule rule : chosenRules) {
			preferences.isActive(rule.getName(), true);
		}
				
		System.out.println("Active rules: " + preferences.getActiveRuleNames());
	}
	
	/**
	 * Update the configured rule set
	 * Update also all configured projects
	 */
	private void updateRuleSet() {
		try {
			ProgressMonitorDialog monitorDialog = new ProgressMonitorDialog(getShell());
			monitorDialog.run(true, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					plugin.getPreferencesManager().setRuleSet(tableManager.ruleSet());
				}
			});
		} catch (Exception e) {
			plugin.logError("Exception updating all projects after a preference change", e);
		}
	}

}