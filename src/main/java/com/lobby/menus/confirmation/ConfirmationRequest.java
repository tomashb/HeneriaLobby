package com.lobby.menus.confirmation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ConfirmationRequest {

    private final String templateId;
    private final String previousMenuId;
    private final String actionDescription;
    private final String actionTitle;
    private final String actionIcon;
    private final List<String> actionDetails;
    private final List<String> confirmActions;
    private final List<String> cancelActions;

    private ConfirmationRequest(final Builder builder) {
        this.templateId = builder.templateId == null || builder.templateId.isBlank()
                ? "confirmation_template"
                : builder.templateId;
        this.previousMenuId = builder.previousMenuId;
        this.actionDescription = builder.actionDescription;
        this.actionTitle = builder.actionTitle;
        this.actionIcon = builder.actionIcon;
        this.actionDetails = Collections.unmodifiableList(new ArrayList<>(builder.actionDetails));
        this.confirmActions = Collections.unmodifiableList(new ArrayList<>(builder.confirmActions));
        this.cancelActions = Collections.unmodifiableList(new ArrayList<>(builder.cancelActions));
    }

    public String templateId() {
        return templateId;
    }

    public String previousMenuId() {
        return previousMenuId;
    }

    public String actionDescription() {
        return actionDescription;
    }

    public String actionTitle() {
        return actionTitle;
    }

    public String actionIcon() {
        return actionIcon;
    }

    public List<String> actionDetails() {
        return actionDetails;
    }

    public List<String> confirmActions() {
        return confirmActions;
    }

    public List<String> cancelActions() {
        return cancelActions;
    }

    public String joinedDetails() {
        if (actionDetails.isEmpty()) {
            return "";
        }
        return String.join("\n", actionDetails);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String templateId;
        private String previousMenuId;
        private String actionDescription;
        private String actionTitle;
        private String actionIcon;
        private final List<String> actionDetails = new ArrayList<>();
        private final List<String> confirmActions = new ArrayList<>();
        private final List<String> cancelActions = new ArrayList<>();

        public Builder templateId(final String templateId) {
            this.templateId = templateId;
            return this;
        }

        public Builder previousMenuId(final String previousMenuId) {
            this.previousMenuId = previousMenuId;
            return this;
        }

        public Builder actionDescription(final String actionDescription) {
            this.actionDescription = actionDescription;
            return this;
        }

        public Builder actionTitle(final String actionTitle) {
            this.actionTitle = actionTitle;
            return this;
        }

        public Builder actionIcon(final String actionIcon) {
            this.actionIcon = actionIcon;
            return this;
        }

        public Builder addDetail(final String detail) {
            if (detail != null) {
                actionDetails.add(detail);
            }
            return this;
        }

        public Builder addDetails(final List<String> details) {
            if (details != null) {
                details.stream().filter(Objects::nonNull).forEach(actionDetails::add);
            }
            return this;
        }

        public Builder addConfirmAction(final String action) {
            if (action != null && !action.isBlank()) {
                confirmActions.add(action.trim());
            }
            return this;
        }

        public Builder addConfirmActions(final List<String> actions) {
            if (actions != null) {
                actions.stream().filter(Objects::nonNull).forEach(action -> addConfirmAction(action));
            }
            return this;
        }

        public Builder addCancelAction(final String action) {
            if (action != null && !action.isBlank()) {
                cancelActions.add(action.trim());
            }
            return this;
        }

        public Builder addCancelActions(final List<String> actions) {
            if (actions != null) {
                actions.stream().filter(Objects::nonNull).forEach(action -> addCancelAction(action));
            }
            return this;
        }

        public ConfirmationRequest build() {
            return new ConfirmationRequest(this);
        }
    }
}
