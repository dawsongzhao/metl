package org.jumpmind.symmetric.is.ui.views.design;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.jumpmind.symmetric.is.core.model.AbstractObject;
import org.jumpmind.symmetric.is.core.model.Flow;
import org.jumpmind.symmetric.is.core.model.FlowParameter;
import org.jumpmind.symmetric.is.ui.common.ApplicationContext;
import org.jumpmind.symmetric.is.ui.common.ButtonBar;
import org.jumpmind.symmetric.ui.common.ImmediateUpdateTextField;
import org.jumpmind.symmetric.ui.common.ResizableWindow;

import com.vaadin.data.Container;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.event.FieldEvents.FocusEvent;
import com.vaadin.event.FieldEvents.FocusListener;
import com.vaadin.event.Transferable;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptAll;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.AbstractSelect.AbstractSelectTargetDetails;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Field;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.TableDragMode;
import com.vaadin.ui.TableFieldFactory;
import com.vaadin.ui.TextField;
import com.vaadin.ui.themes.ValoTheme;

@SuppressWarnings("serial")
class EditParametersWindow extends ResizableWindow implements ValueChangeListener {

    ApplicationContext context;
    
    Flow flow;

    BeanItemContainer<FlowParameter> container;

    Table table;
    
    Button insertButton;
    
    Button removeButton;

    public EditParametersWindow(ApplicationContext context, Flow flow) {
        super("Flow Parameters");
        this.context = context;
        this.flow = flow;

        Button closeButton = new Button("Close");
        closeButton.addStyleName(ValoTheme.BUTTON_PRIMARY);
        closeButton.addClickListener(new CloseClickListener());

        ButtonBar buttonBar = new ButtonBar();
        buttonBar.addButton("Add", FontAwesome.PLUS, new AddClickListener());
        insertButton = buttonBar.addButton("Insert", FontAwesome.CHEVRON_RIGHT, new InsertClickListener());
        insertButton.setEnabled(false);
        removeButton = buttonBar.addButton("Remove", FontAwesome.TRASH_O, new RemoveClickListener());
        removeButton.setEnabled(false);
        addComponent(buttonBar);

        table = new Table();
        table.setSizeFull();
        container = new BeanItemContainer<FlowParameter>(FlowParameter.class);
        table.setContainerDataSource(container);
        table.setEditable(true);
        table.setSelectable(true);
        table.setSortEnabled(false);
        table.setDragMode(TableDragMode.ROW);
        table.setDropHandler(new TableDropHandler());
        table.setTableFieldFactory(new EditFieldFactory());
        table.setVisibleColumns("position", "name", "defaultValue");
        table.setColumnHeaders("#", "Name", "Default Value");
        table.setColumnExpandRatio("name", .3f);
        table.setColumnExpandRatio("defaultValue", .6f);
        table.addValueChangeListener(this);
        addComponent(table, 1);

        addComponent(buildButtonFooter(closeButton));

        List<FlowParameter> params = flow.getFlowParameters();
        Collections.sort(params, new Comparator<FlowParameter>() {
            public int compare(FlowParameter o1, FlowParameter o2) {
                return new Integer(o1.getPosition()).compareTo(new Integer(o2.getPosition()));
            }
        });

        for (FlowParameter flowParameter : params) {
            table.addItem(flowParameter);
        }
    }

    public void valueChange(ValueChangeEvent event) {
        removeButton.setEnabled(table.getValue() != null);
        insertButton.setEnabled(table.getValue() != null);
    }

    protected void addItem(int index) {
        FlowParameter parameter = new FlowParameter();
        parameter.setFlowId(flow.getId());
        parameter.setName("Parameter " + (index + 1));
        parameter.setPosition((index + 1));
        context.getConfigurationService().save(parameter);
        flow.getFlowParameters().add(parameter);
        container.addItemAt(index, parameter);      
        table.select(parameter);
        table.setCurrentPageFirstItemId(parameter);
    }

    protected void saveAllPositions() {
        @SuppressWarnings("unchecked")
        Collection<FlowParameter> parameters = (Collection<FlowParameter>) table.getItemIds();
        int count = 1;
        for (FlowParameter parameter : parameters) {
            parameter.setPosition(count++);
            context.getConfigurationService().save(parameter);
        }
    }
    
    class AddClickListener implements ClickListener {
        public void buttonClick(ClickEvent event) {
            addItem(flow.getFlowParameters().size());
        }
    }

    class InsertClickListener implements ClickListener {
        public void buttonClick(ClickEvent event) {
            addItem(container.indexOfId((FlowParameter) table.getValue()));
            saveAllPositions();
        }
    }

    class RemoveClickListener implements ClickListener {
        public void buttonClick(ClickEvent event) {
            FlowParameter parameter = (FlowParameter) table.getValue();
            if (parameter != null) {
                flow.getFlowParameters().remove(parameter);
                context.getConfigurationService().delete((AbstractObject) parameter);
                int index = container.indexOfId(parameter);
                table.removeItem(parameter);
                if (index < container.size()) {
                    table.select(container.getIdByIndex(index));
                }
                saveAllPositions();
            }
        }
    }

    class CloseClickListener implements ClickListener {
        public void buttonClick(ClickEvent event) {
            EditParametersWindow.this.close();
        }
    }

    class EditFieldFactory implements TableFieldFactory {
        public Field<?> createField(final Container dataContainer, final Object itemId,
                final Object propertyId, com.vaadin.ui.Component uiContext) {
            final FlowParameter parameter = (FlowParameter) itemId;
            final TextField textField = new ImmediateUpdateTextField(null) {
                protected void save(String text) {
                    parameter.setDefaultValue(text);
                    context.getConfigurationService().save(parameter);
                }
            };
            textField.addFocusListener(new FocusListener() {
                public void focus(FocusEvent event) {
                    table.select(itemId);                    
                }                
            });
            if (propertyId.equals("position")) {
                textField.setReadOnly(true);
                textField.setWidth(3, Unit.EM);
            } else {
                textField.setWidth(100, Unit.PERCENTAGE);
            }
            return textField;
        }
    }

    class TableDropHandler implements DropHandler {
        public void drop(DragAndDropEvent event) {
            AbstractSelectTargetDetails targetDetails = (AbstractSelectTargetDetails) event.getTargetDetails();
            Transferable transferable = event.getTransferable();
            if (transferable.getSourceComponent() == table) {
                FlowParameter target = (FlowParameter) targetDetails.getItemIdOver();
                int targetIndex = container.indexOfId(target);
                FlowParameter source = (FlowParameter) transferable.getData("itemId");
                if (targetIndex == -1) {
                    targetIndex = 0;
                }
                container.removeItem(source);
                container.addItemAt(targetIndex, source);
                table.select(source);
                saveAllPositions();
            }
        }

        public AcceptCriterion getAcceptCriterion() {
            return AcceptAll.get();
        }
    }

}