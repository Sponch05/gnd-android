/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.gnd.ui.editrecord.input;

import static java8.util.stream.StreamSupport.stream;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import com.google.android.gnd.R;
import com.google.android.gnd.vo.Form.Field;
import com.google.android.gnd.vo.Form.MultipleChoice;
import com.google.android.gnd.vo.Form.MultipleChoice.Option;
import com.google.android.gnd.vo.Record.Choices;
import com.google.android.gnd.vo.Record.Value;
import java.util.List;
import java8.util.Optional;
import java8.util.function.Consumer;

class SingleSelectDialogFactory {
  private Context context;

  SingleSelectDialogFactory(Context context) {
    this.context = context;
  }

  AlertDialog create(
    Field field, Optional<Value> initialValue, Consumer<Optional<Value>> valueChangeCallback) {
    MultipleChoice multipleChoice = field.getMultipleChoice();
    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
    List<Option> options = multipleChoice.getOptions();
    DialogState state = new DialogState(multipleChoice, initialValue);
    dialogBuilder.setSingleChoiceItems(
      getLabels(multipleChoice), state.checkedItem, state::onSelect);
    dialogBuilder.setCancelable(false);
    dialogBuilder.setTitle(field.getLabel());
    dialogBuilder.setPositiveButton(
      R.string.apply_multiple_choice_changes,
      (dialog, which) -> valueChangeCallback.accept(state.getSelectedValue(options)));
    dialogBuilder.setNegativeButton(
      R.string.discard_multiple_choice_changes, (dialog, which) -> {
      });
    return dialogBuilder.create();
  }

  private String[] getLabels(MultipleChoice multipleChoice) {
    return stream(multipleChoice.getOptions()).map(Option::getLabel).toArray(String[]::new);
  }

  private static class DialogState {
    private int checkedItem;

    public DialogState(MultipleChoice multipleChoice, Optional<Value> initialValue) {
      checkedItem =
        initialValue.flatMap(Value::getFirstCode).flatMap(multipleChoice::getIndex).orElse(-1);
    }

    private void onSelect(DialogInterface dialog, int which) {
      if (checkedItem == which) {
        // Allow user to toggle values off by tapping selected item.
        checkedItem = -1;
        ((AlertDialog) dialog).getListView().setItemChecked(which, false);
      } else {
        checkedItem = which;
      }
    }

    private Optional<Value> getSelectedValue(List<Option> options) {
      Choices.Builder choices = Choices.newBuilder();
      if (checkedItem >= 0) {
        choices.codesBuilder().add(options.get(checkedItem).getCode());
        return Optional.of(Value.ofChoices(choices.build()));
      } else {
        return Optional.empty();
      }
    }
  }
}
