package org.continuity.experimentation.experiment.apichanger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.continuity.annotation.dsl.system.HttpInterface;

public class MarkovTemplateChanger {

	private final MarkovTemplate template;

	public MarkovTemplateChanger(MarkovTemplate template) {
		this.template = template;
	}

	public MarkovTemplateChanger(Path templatePath) throws IOException {
		this(new MarkovTemplate(templatePath));
	}

	public void copyInterface(HttpInterface origInterf, HttpInterface newInterf) {
		List<String> origRow = template.getRow(origInterf.getId());
		List<String> newRow = new ArrayList<>(origRow);
		newRow.set(0, newInterf.getId());
		template.insertRow(newRow);

		List<String> origColumn = template.getColumn(origInterf.getId());
		List<String> newColumn = new ArrayList<>(origColumn);
		newColumn.set(0, newInterf.getId());
		template.insertColumn(newColumn);
	}

	public void removeInterface(HttpInterface interf) {
		template.removeRow(interf.getId());
		template.removeColumn(interf.getId());
	}

	public MarkovTemplate getTemplate() {
		return template;
	}

}
