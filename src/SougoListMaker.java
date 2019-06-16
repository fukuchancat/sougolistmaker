import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.regex.Pattern;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import javax.imageio.ImageIO;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.UserList;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;

public class SougoListMaker extends Application {
	protected final static String CONSUMER_KEY = "<CONSUMER_KEY>";
	protected final static String CONSUMER_SECRET = "<CONSUMER_SECRET>";

	private Account account;
	private ArrayList<Account> accounts = new ArrayList<Account>();

	private static TextArea messageArea;
	private VBox accountBox;
	private ToggleGroup accountGroup;
	private BorderPane detailPane;
	private ImageView biggerIcon;
	private Label whiteLabel;
	private Label blackLabel;

	private Stage listStage = new Stage(StageStyle.UTILITY);
	private Stage whiteStage = new Stage(StageStyle.UTILITY);
	private Stage blackStage = new Stage(StageStyle.UTILITY);
	private Stage accountStage = new Stage(StageStyle.UTILITY);
	private Stage settingStage = new Stage(StageStyle.UTILITY);

	private String boxStyle = "-fx-border-color: #336699;-fx-border-width: 1px;-fx-border-radius: 5px;-fx-padding: 10px;";
	private String headingStyle = "-fx-font-size: 13px;-fx-padding: 0 0 4px 0;";
	private String editButtonStyle = "-fx-min-width: 30px;-fx-min-height: 30px;-fx-font-size: 12px;-fx-wrap-text: true;";

	private SystemTray systemTray = SystemTray.getSystemTray();
	private TrayIcon trayIcon;
	private boolean startupEnabled = false;
	private boolean residentEnabled = true;
	private int period = 60;

	private FileOutputStream fos;
	private FileChannel fc;
	private FileLock fl;

	ScheduledService<Void> schedule = new ScheduledService<Void>() {
		@Override
		protected Task<Void> createTask() {
			return getTask();
		}
	};
	Service<Void> service = new Service<Void>() {
		@Override
		protected Task<Void> createTask() {
			return getTask();
		}
	};

	@Override
	public void start(Stage mainStage) {
		setLock(true);
		Scene scene = new Scene(setRoot());
		initialize(mainStage, scene);

		loadData();

		if (accounts.isEmpty()) {
			messageArea.appendText("�܂��́u�A�J�E���g�v���́u+�v�{�^������A�J�E���g��ǉ����Ă�������\n");
		} else {
			account = accounts.get(0);
			connect();
			listAccounts();
			setAccountDetail();
			setListDetail();
			setExceptionDetail();
		}
	}

	void initialize(Stage mainStage, Scene scene) {
		PopupMenu popup = new PopupMenu();
		java.awt.MenuItem openItem = new java.awt.MenuItem("Show");
		java.awt.MenuItem exitItem = new java.awt.MenuItem("Exit");

		try {
			java.awt.Image icon = ImageIO.read(new File("files:/../resources/trayicon.png"));
			trayIcon = new TrayIcon(icon);
		} catch (Exception e) {
			e.printStackTrace();
		}
		trayIcon.addActionListener(event -> Platform.runLater(() -> showStage(mainStage)));
		openItem.addActionListener(event -> Platform.runLater(() -> showStage(mainStage)));
		exitItem.addActionListener(event -> Platform.runLater(() -> exit()));

		popup.add(openItem);
		popup.add(exitItem);
		trayIcon.setPopupMenu(popup);

		mainStage.setTitle("���݃��X�g���[�J�[");
		mainStage.setScene(scene);
		mainStage.setOnCloseRequest(setCloseEventHandler(mainStage));

		Platform.setImplicitExit(false);

		if (getParameters().getRaw().contains("startup")) {
			try {
				systemTray.add(trayIcon);
			} catch (AWTException e) {
				e.printStackTrace();
			}
		} else {
			mainStage.show();
		}
	}

	void showStage(Stage mainStage) {
		mainStage.show();
		systemTray.remove(trayIcon);
	}

	VBox setRoot() {
		VBox root = new VBox();

		HBox topBox = setTopBox();
		HBox bottomBox = setBottomBox();

		VBox.setVgrow(bottomBox, Priority.ALWAYS);
		root.getChildren().addAll(topBox, bottomBox);

		return root;
	}

	HBox setTopBox() {
		HBox topBox = new HBox();

		ScrollPane accountPane = setAccountPane();
		VBox settingBox = setSettingBox();

		HBox.setHgrow(settingBox, Priority.ALWAYS);
		topBox.getChildren().addAll(accountPane, settingBox);

		return topBox;
	}

	ScrollPane setAccountPane() {
		accountBox = new VBox();
		accountGroup = new ToggleGroup();
		ScrollPane accountPane = new ScrollPane();
		HBox hBox = new HBox(4);
		Label label = new Label("�A�J�E���g");
		Button plusButton = new Button("\u2795");
		Button minusButton = new Button("\u2796");

		accountPane.setContent(accountBox);
		hBox.setMaxWidth(Double.MAX_VALUE);
		hBox.setStyle("-fx-padding: 5px");
		hBox.setAlignment(Pos.CENTER);
		label.setStyle("-fx-font-size: 13px;");
		label.setMaxWidth(Double.MAX_VALUE);

		HBox.setHgrow(label, Priority.ALWAYS);

		plusButton.setOnAction(setAddAcountEventHandler());
		minusButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				accounts.remove(accountGroup.getSelectedToggle().getUserData());
				accountBox.getChildren().remove(accountGroup.getSelectedToggle());

				accountGroup.getToggles().get(accounts.size() - 1).setSelected(true);
			}
		});

		accountGroup.selectedToggleProperty().addListener(new ChangeListener<Toggle>() {
			@Override
			public void changed(ObservableValue<? extends Toggle> observable, Toggle oldValue,
					Toggle newValue) {
				account = (Account) newValue.getUserData();
				setAccountDetail();
				setListDetail();
				setExceptionDetail();
			}
		});

		hBox.getChildren().addAll(label, plusButton, minusButton);
		accountBox.getChildren().add(hBox);

		return accountPane;
	}

	EventHandler<ActionEvent> setAddAcountEventHandler() {
		EventHandler<ActionEvent> addAcountEventHandler = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				if (accountStage.isShowing()) {
					return;
				}
				accountStage.setAlwaysOnTop(true);

				VBox vBox = new VBox(4);
				Label label = new Label("Pin:");
				TextField textField = new TextField();

				textField.setMaxWidth(Double.MAX_VALUE);
				vBox.setStyle("-fx-padding: 10px 10px 20px;");
				vBox.getChildren().addAll(label, textField);

				Twitter twitter = new TwitterFactory().getInstance();
				twitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
				RequestToken requestToken;

				try {
					requestToken = twitter.getOAuthRequestToken();
					Desktop.getDesktop().browse(new URI(requestToken.getAuthorizationURL()));

					textField.textProperty().addListener(new ChangeListener<String>() {
						@Override
						public void changed(ObservableValue<? extends String> observable,
								String oldValue, String newValue) {
							try {
								AccessToken token = twitter.getOAuthAccessToken(requestToken,
										newValue);

								account = new Account(token.getToken(), token.getTokenSecret());
								accounts.add(account);
								ToggleButton accountButton = getAccountButton(account);
								accountBox.getChildren().add(accountButton);
								accountButton.setSelected(true);

								accountStage.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					});
					accountStage.setScene(new Scene(vBox));
					accountStage.show();

				} catch (Exception e) {
					messageArea.appendText("���N�G�X�g�g�[�N���̎擾�Ɏ��s\n");
				}
			}
		};
		return addAcountEventHandler;
	}

	VBox setSettingBox() {
		VBox settingBox = new VBox(8);
		BorderPane listPane = setListPane();
		VBox exceptionPane = setExceptionBox();

		settingBox.setStyle("-fx-padding: 10px;");
		settingBox.getChildren().addAll(listPane, exceptionPane);

		return settingBox;
	}

	BorderPane setListPane() {
		BorderPane listPane = new BorderPane();
		Label label = new Label("�Ώۂ̃��X�g");
		Image image = new Image("file:/../resources/egg_step_1.png", 64, 64, false, true);
		biggerIcon = new ImageView(image);
		detailPane = new BorderPane();
		Button button = new Button("���X�g�ݒ�");

		detailPane.setStyle("-fx-padding: 0 0 0 10px");
		listPane.setStyle(boxStyle);
		label.setStyle(headingStyle);

		button.setMaxHeight(Double.MAX_VALUE);
		button.setOnAction(setListSettingEventHandler());

		listPane.setTop(label);
		listPane.setLeft(biggerIcon);
		listPane.setCenter(detailPane);
		listPane.setRight(button);

		return listPane;
	}

	EventHandler<ActionEvent> setListSettingEventHandler() {
		EventHandler<ActionEvent> eventHandler = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (listStage.isShowing()) {
					return;
				}

				Account currentAccount = account;
				HBox hBox = new HBox(4);
				ListView<UserList> listView = new ListView<>();
				ObservableList<UserList> observableList = FXCollections.observableArrayList();

				hBox.setStyle("-fx-padding: 5px");
				HBox.setHgrow(listView, Priority.ALWAYS);

				try {
					for (UserList userList : currentAccount.userLists) {
						observableList.add(userList);
					}
				} catch (Exception e) {
					addText("���X�g�ꗗ�̎擾�Ɏ��s");
				}

				listView.setItems(observableList);
				try {
					listView.getSelectionModel().select(
							currentAccount.twitter.showUserList(currentAccount.userListId));
				} catch (Exception e) {
				}
				listView.setCellFactory((ListView<UserList> l) -> new UserListCell());

				listView.getSelectionModel().selectedItemProperty()
						.addListener(new ChangeListener<UserList>() {
							@Override
							public void changed(ObservableValue<? extends UserList> observable,
									UserList oldValue, UserList newValue) {
								currentAccount.setUserListId(newValue.getId());
								setListDetail();
							}
						});

				VBox vBox = new VBox(3);
				Button plusButton = new Button("\u2795");
				Button minusButton = new Button("\u2796");

				vBox.setAlignment(Pos.BOTTOM_CENTER);
				plusButton.setMaxWidth(Double.MAX_VALUE);
				plusButton.setStyle(editButtonStyle);
				minusButton.setMaxWidth(Double.MAX_VALUE);
				minusButton.setStyle(editButtonStyle);

				plusButton.setOnAction(setCreateListEventHandler(listStage, observableList));

				minusButton.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						UserList userList = listView.getSelectionModel().getSelectedItem();
						observableList.remove(userList);

						try {
							currentAccount.twitter.destroyUserList(userList.getId());
							observableList.remove(userList);
						} catch (TwitterException e) {
							addText("���X�g�̍폜�Ɏ��s");
						}
					}
				});

				vBox.getChildren().addAll(plusButton, minusButton);

				hBox.getChildren().addAll(listView, vBox);
				listStage.setScene(new Scene(hBox));
				listStage.show();
			}
		};

		return eventHandler;
	}

	EventHandler<ActionEvent> setCreateListEventHandler(Stage owner,
			ObservableList<UserList> observableList) {
		EventHandler<ActionEvent> createListEventHandler = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				String white = "-fx-text-fill: white;";

				Stage stage = new Stage(StageStyle.TRANSPARENT);
				stage.initModality(Modality.APPLICATION_MODAL);
				stage.initOwner(owner);

				VBox vBox = new VBox(8);
				VBox nameBox = new VBox();
				VBox descriptionBox = new VBox();
				HBox toggleBox = new HBox(15);
				HBox buttonBox = new HBox(4);
				Label nameLabel = new Label("���X�g��");
				Label descriptionLabel = new Label("����");
				TextField textField = new TextField();
				TextArea textArea = new TextArea();
				ToggleGroup toggleGroup = new ToggleGroup();
				RadioButton publicButton = new RadioButton("���J");
				RadioButton privateButton = new RadioButton("����J");
				Button addButton = new Button("���X�g�쐬");
				Button cancelButton = new Button("�L�����Z��");

				vBox.setMaxSize(owner.getWidth() * 0.8, owner.getWidth() * 0.9);
				vBox.setStyle("-fx-background-radius: 5px;-fx-background-color: rgba(0,0,0,0.7);-fx-padding: 10px");

				nameLabel.setStyle(white);
				descriptionLabel.setStyle(white);
				publicButton.setStyle(white);
				privateButton.setStyle(white);

				addButton.setMaxWidth(Double.MAX_VALUE);
				cancelButton.setMaxWidth(Double.MAX_VALUE);
				HBox.setHgrow(addButton, Priority.ALWAYS);
				HBox.setHgrow(cancelButton, Priority.ALWAYS);

				textField.setPromptText("25�����ȓ��œ��͕K�{");
				privateButton.setSelected(true);
				publicButton.setToggleGroup(toggleGroup);
				privateButton.setToggleGroup(toggleGroup);
				addButton.setDisable(true);

				nameBox.getChildren().addAll(nameLabel, textField);
				descriptionBox.getChildren().addAll(descriptionLabel, textArea);
				toggleBox.getChildren().addAll(publicButton, privateButton);
				buttonBox.getChildren().addAll(addButton, cancelButton);
				vBox.getChildren().addAll(nameBox, descriptionBox, toggleBox, buttonBox);

				addButton.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						try {
							UserList userList = account.twitter.createUserList(textField.getText(),
									publicButton.isSelected(), textArea.getText());
							observableList.add(userList);

							stage.close();
						} catch (TwitterException e) {
							addText("���X�g�̍쐬�Ɏ��s���܂���");
						}
					}
				});

				cancelButton.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						stage.close();
					}
				});

				textField.textProperty().addListener(new ChangeListener<String>() {
					@Override
					public void changed(ObservableValue<? extends String> observable,
							String oldValue, String newValue) {
						if (0 < newValue.length() && newValue.length() <= 25) {
							addButton.setDisable(false);
						} else {
							addButton.setDisable(true);
						}
					}
				});

				Scene scene = new Scene(vBox);
				scene.setFill(null);
				stage.setScene(scene);
				stage.show();
			}
		};
		return createListEventHandler;
	}

	VBox setExceptionBox() {
		VBox exceptionBox = new VBox();
		VBox whiteBox = new VBox();
		VBox blackBox = new VBox();
		HBox hBox = new HBox(8);
		Label label = new Label("�z���C�g���X�g/�u���b�N���X�g");
		whiteLabel = new Label("0�l�o�^��");
		blackLabel = new Label("0�l�o�^��");
		Button whiteButton = new Button("�z���C�g���X�g�ݒ�");
		Button blackButton = new Button("�u���b�N���X�g�ݒ�");

		exceptionBox.setStyle(boxStyle);
		label.setStyle(headingStyle);

		whiteButton.setMaxWidth(Double.MAX_VALUE);
		whiteButton.setPrefHeight(40);
		whiteBox.setAlignment(Pos.CENTER);
		blackButton.setMaxWidth(Double.MAX_VALUE);
		blackButton.setPrefHeight(40);
		blackBox.setAlignment(Pos.CENTER);

		HBox.setHgrow(whiteBox, Priority.ALWAYS);
		HBox.setHgrow(blackBox, Priority.ALWAYS);

		whiteButton.setOnAction(setExceptionEventHandler(whiteStage, Account.WHITE_LIST));
		blackButton.setOnAction(setExceptionEventHandler(blackStage, Account.BLACK_LIST));

		whiteBox.getChildren().addAll(whiteButton, whiteLabel);
		blackBox.getChildren().addAll(blackButton, blackLabel);
		hBox.getChildren().addAll(whiteBox, blackBox);
		exceptionBox.getChildren().addAll(label, hBox);

		return exceptionBox;
	}

	EventHandler<ActionEvent> setExceptionEventHandler(Stage stage, int target) {
		EventHandler<ActionEvent> exceptionEventHandler = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent event) {
				if (stage.isShowing()) {
					return;
				}

				ArrayList<Long> list;
				if (target == Account.WHITE_LIST) {
					list = account.whiteList;
				} else {
					list = account.blackList;
				}

				VBox vBox = new VBox(4);
				HBox addBox = new HBox(4);
				HBox textFieldBox = new HBox();
				Button plusButton = new Button("\u2795");
				Button minusButton = new Button("\u2796");
				Label label = new Label("@");
				TextField textField = new TextField();

				TableView<ExceptionUser> tableView = new TableView<ExceptionUser>();
				TableColumn<ExceptionUser, String> screenNameColumn = new TableColumn<ExceptionUser, String>(
						"�X�N���[���l�[��");
				TableColumn<ExceptionUser, String> nameColumn = new TableColumn<ExceptionUser, String>(
						"���O");
				ObservableList<ExceptionUser> observableList = FXCollections.observableArrayList();

				tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
				screenNameColumn.setCellValueFactory(new PropertyValueFactory<>("screenName"));
				nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

				tableView.getColumns().add(screenNameColumn);
				tableView.getColumns().add(nameColumn);

				textFieldBox.setAlignment(Pos.CENTER);
				label.setWrapText(true);
				textField.setPromptText("�ǉ����郆�[�U�̃X�N���[���l�[��");
				vBox.setStyle("-fx-padding: 5px");
				plusButton.setStyle(editButtonStyle);
				minusButton.setStyle(editButtonStyle);

				HBox.setHgrow(textField, Priority.ALWAYS);
				HBox.setHgrow(textFieldBox, Priority.ALWAYS);

				textFieldBox.getChildren().addAll(label, textField);
				addBox.getChildren().addAll(textFieldBox, plusButton, minusButton);
				vBox.getChildren().addAll(tableView, addBox);

				for (Long l : list) {
					try {
						User user = account.twitter.showUser(l);
						observableList.add(new ExceptionUser(user.getScreenName(), user.getName()));
					} catch (Exception e) {
						addText("���[�U�[ID:" + l + " �̏�񂪎擾�ł��܂���ł���");
					}
				}
				tableView.setItems(observableList);

				plusButton.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						if (!textField.getText().isEmpty()) {
							try {
								User user = account.twitter.showUser(textField.getText());
								long id = user.getId();
								textField.clear();

								if (list.contains(id)) {
									addText("@" + user.getScreenName() + " �͊��ɓo�^����Ă��܂�");
								} else {
									observableList.add(new ExceptionUser(user.getScreenName(), user
											.getName()));
									list.add(user.getId());
									setExceptionDetail();
								}
							} catch (Exception e) {
								addText("���[�U�[��񂪎擾�ł��܂���ł����BID���m�F���Ă�������");
							}
						}
					}
				});
				textField.setOnAction(plusButton.getOnAction());

				minusButton.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent event) {
						int index = tableView.getSelectionModel().getSelectedIndex();

						if (index != -1) {
							observableList.remove(index);
							list.remove(index);

							setExceptionDetail();
						}
					}
				});

				stage.setScene(new Scene(vBox));
				stage.show();
			}
		};

		return exceptionEventHandler;
	}

	HBox setBottomBox() {
		HBox bottomBox = new HBox();
		VBox buttonBox = new VBox();
		Button setting = new Button("��{�ݒ�");
		Button run = new Button("���������s");
		messageArea = new TextArea();

		messageArea.setPrefHeight(140);
		bottomBox.setStyle("-fx-background-color: #336699;");
		buttonBox.setAlignment(Pos.BOTTOM_CENTER);
		buttonBox.setPrefWidth(100);
		buttonBox.setMinWidth(70);
		setting.setMaxWidth(Double.MAX_VALUE);
		run.setMaxWidth(Double.MAX_VALUE);

		HBox.setHgrow(messageArea, Priority.ALWAYS);

		run.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				service.restart();
			}
		});
		setting.setOnAction(getSettingEventHandler());

		buttonBox.getChildren().addAll(setting, run);
		bottomBox.getChildren().addAll(messageArea, buttonBox);

		return bottomBox;
	}

	EventHandler<ActionEvent> getSettingEventHandler() {
		EventHandler<ActionEvent> settingEventHandler = new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent arg0) {
				if (settingStage.isShowing()) {
					return;
				}
				VBox vBox = new VBox(5);
				VBox periodBox = new VBox(2);
				HBox hbox = new HBox(5);
				CheckBox startupCheckBox = new CheckBox("�X�^�[�g�A�b�v�ɓo�^");
				CheckBox residentCheckBox = new CheckBox("�^�X�N�g���C�ɏ풓���Ď������s");
				Label periodLabel = new Label("�������s�̊Ԋu");
				Label minuteLabel = new Label("��");

				TextField textField = new TextField(String.valueOf(period)) {
					Pattern pattern = Pattern.compile("[^0-9]");

					@Override
					public void replaceText(int start, int end, String text) {
						if (!pattern.matcher(text).find()) {
							super.replaceText(start, end, text);
						}
					}

					@Override
					public void replaceSelection(String text) {
						if (!pattern.matcher(text).find()) {
							super.replaceSelection(text);
						}
					}
				};

				startupCheckBox.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						if (startupCheckBox.isSelected()) {
							String value = "\"javaw -jar " + System.getProperty("user.dir")
									+ "\\SougoListMaker.jar startup\"";
							try {
								WinRegistry.writeStringValue(WinRegistry.HKEY_CURRENT_USER,
										"Software\\Microsoft\\Windows\\CurrentVersion\\Run",
										"SougoListMaker autorun key", value);
								startupEnabled = true;
							} catch (Exception e) {
								messageArea.appendText("�X�^�[�g�A�b�v�ɓo�^�ł��܂���ł���\n");
								startupEnabled = false;
								startupCheckBox.setSelected(false);
							}
						} else {
							try {
								WinRegistry.deleteValue(WinRegistry.HKEY_CURRENT_USER,
										"Software\\Microsoft\\Windows\\CurrentVersion\\Run",
										"SougoListMaker autorun key");
								startupEnabled = false;
							} catch (Exception e) {
								messageArea.appendText("�X�^�[�g�A�b�v����폜�ł��܂���ł���\n");
								startupEnabled = true;
								startupCheckBox.setSelected(true);
							}
						}
					}
				});
				residentCheckBox.setOnAction(new EventHandler<ActionEvent>() {
					@Override
					public void handle(ActionEvent arg0) {
						residentEnabled = residentCheckBox.isSelected();

						if (residentCheckBox.isSelected()) {
							startupCheckBox.setDisable(false);
						} else {
							startupCheckBox.setSelected(false);
							startupCheckBox.setDisable(true);
						}
					}
				});

				vBox.setPrefWidth(220);
				minuteLabel.setWrapText(true);
				vBox.setStyle("-fx-padding: 10 10 20 10");
				periodBox.setStyle("-fx-padding: 0 0 0 10");
				hbox.setAlignment(Pos.CENTER_LEFT);

				startupCheckBox.setSelected(startupEnabled);
				residentCheckBox.setSelected(residentEnabled);

				hbox.getChildren().addAll(textField, minuteLabel);
				periodBox.getChildren().addAll(periodLabel, hbox);
				vBox.getChildren().addAll(startupCheckBox, residentCheckBox, periodBox);

				settingStage.setScene(new Scene(vBox));
				settingStage.show();
			}
		};
		return settingEventHandler;
	}

	@SuppressWarnings("unchecked")
	// �f�[�^��ǂݍ���
	void loadData() {
		try {
			File data = new File("file:/../resources/data");

			if (data.exists()) {
				XMLDecoder decorder = new XMLDecoder(new BufferedInputStream(new FileInputStream(
						data)));

				accounts = (ArrayList<Account>) decorder.readObject();
				startupEnabled = (boolean) decorder.readObject();
				residentEnabled = (boolean) decorder.readObject();

				decorder.close();
			} else {
				messageArea.appendText("���[�J���̕ۑ��f�[�^��������܂���ł���\n");
				data.createNewFile();
			}
		} catch (Exception e) {
			messageArea.appendText("���[�J������ǂݍ��߂Ȃ��f�[�^������܂���\n");
		}
	}

	// �f�[�^��ۑ�����
	void saveData() {
		try {
			XMLEncoder encoder = new XMLEncoder(new BufferedOutputStream(new FileOutputStream(
					"file:/../resources/data")));

			encoder.writeObject(accounts);
			encoder.writeObject(startupEnabled);
			encoder.writeObject(residentEnabled);

			encoder.close();
		} catch (Exception e) {
			messageArea.appendText("���[�J���ւ̃f�[�^�ۑ��Ɏ��s\n");
		}
	}

	// Twitter�ɐڑ�
	void connect() {
		for (Account account : accounts) {
			try {
				account.connect();
				messageArea.appendText("[@" + account.screenName + "] �ڑ����܂���\n");
			} catch (Exception e) {
				messageArea.appendText("�ڑ��Ɏ��s���܂���\n");
			}
		}
	}

	void listAccounts() {
		for (Account account : accounts) {
			try {
				accountBox.getChildren().add(getAccountButton(account));
			} catch (Exception e) {
				addText("���[�U���̎擾�Ɏ��s");
			}
		}
		accountGroup.selectToggle(accountGroup.getToggles().get(0));
	}

	ToggleButton getAccountButton(Account account) {
		String iconUrl = account.profileImageURL;
		Image image = new Image(iconUrl, 32, 32, false, true);
		ImageView icon = new ImageView(image);
		RadioButton accountButton = new RadioButton(account.name + "\n@" + account.screenName);

		accountButton.getStyleClass().remove("radio-button");
		accountButton.getStyleClass().add("toggle-button");
		accountButton.setGraphic(icon);
		accountButton.setToggleGroup(accountGroup);
		accountButton.setUserData(account);
		accountButton.setMnemonicParsing(false);
		accountButton.setMaxWidth(Double.MAX_VALUE);
		accountButton.setAlignment(Pos.CENTER_LEFT);

		return accountButton;
	}

	void setAccountDetail() {
		try {
			String iconUrl = account.biggerProfileImageURL;
			Image image = new Image(iconUrl, 64, 64, false, true);
			biggerIcon.setImage(image);

		} catch (Exception e) {
			addText("�v���t�B�[���摜(��)���擾�ł��܂���ł���");
		}
	}

	void setListDetail() {
		if (account.userListId == null | account.userListId == 0L) {
			detailPane.setTop(new Label("�Ώۂ̃��X�g��ݒ肵�Ă�������"));
			detailPane.setLeft(null);
			detailPane.setBottom(null);
		} else {
			try {
				UserList userList = account.twitter.showUserList(account.userListId);

				Label idLabel = new Label("ID: " + account.userListId);
				Label nameLabel = new Label(userList.getName());
				Label memberLabel = new Label(userList.getMemberCount() + "�l�o�^�ρ@"
						+ userList.getSubscriberCount() + "�l�w�ǒ�");

				detailPane.setTop(idLabel);
				detailPane.setLeft(nameLabel);
				detailPane.setBottom(memberLabel);

			} catch (Exception e) {
				e.printStackTrace();
				addText("���X�g���擾�ł��܂���ł���");
			}
		}
	}

	void setExceptionDetail() {
		whiteLabel.setText(account.whiteList.size() + "�l�o�^��");
		blackLabel.setText(account.blackList.size() + "�l�o�^��");
	}

	Task<Void> getTask() {
		Task<Void> task = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				for (Account account : accounts) {
					try {
						account.run();
					} catch (TwitterException e) {
						e.printStackTrace();
						addText(account, "���s�Ɏ��s���܂���");
					}
				}
				return null;
			}
		};
		task.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
			@Override
			public void handle(WorkerStateEvent arg0) {
				setListDetail();
			}
		});
		return task;
	}

	void addText(String str) {
		try {
			addText(account, str);
		} catch (Exception e) {
			String errorMessage = "�G���[�BAPI��������������܂���\n";

			if (!messageArea.getText().contains(errorMessage)) {
				messageArea.appendText(errorMessage);
			}
		}
	}

	static void addText(Account account, String str) {
		try {
			messageArea.appendText("[@" + account.screenName + "] " + str + "\n");
		} catch (Exception e) {
			String errorMessage = "�G���[�BAPI��������������܂���\n";

			if (!messageArea.getText().contains(errorMessage)) {
				messageArea.appendText(errorMessage);
			}
		}
	}

	EventHandler<WindowEvent> setCloseEventHandler(Stage mainStage) {
		EventHandler<WindowEvent> exitEventHandler = new EventHandler<WindowEvent>() {
			@Override
			public void handle(WindowEvent arg0) {
				if (residentEnabled) {
					mainStage.hide();
					try {
						systemTray.add(trayIcon);
					} catch (AWTException e) {
						e.printStackTrace();
					}
				} else {
					exit();
				}
			}
		};
		return exitEventHandler;
	}

	void setLock(boolean isTrue) {
		File lock = new File("file:/../resources/lock");

		if (isTrue) {
			try {
				if (!lock.exists())
					lock.createNewFile();

				fos = new FileOutputStream(lock);
				fc = fos.getChannel();
				fl = fc.tryLock();

				if (fl == null)
					Platform.exit();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			try {
				if (fl != null && fl.isValid())
					fl.release();

				fc.close();
				fos.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	void exit() {
		setLock(false);
		saveData();
		systemTray.remove(trayIcon);
		Platform.exit();
	}

	void setStartupEnabled(boolean startupEnabled) {
		this.startupEnabled = startupEnabled;
	}

	void setResidentEnabled(boolean residentEnabled) {
		this.residentEnabled = residentEnabled;
	}

	Boolean getStartUpEnabled() {
		return startupEnabled;
	}

	Boolean getResidentEnabled() {
		return residentEnabled;
	}

	static class UserListCell extends ListCell<UserList> {
		@Override
		public void updateItem(UserList item, boolean empty) {
			super.updateItem(item, empty);

			if (empty || item == null) {
				setText(null);
			} else {
				setText(item.getId() + "\n" + item.getName());
			}
		}
	}

	public static class ExceptionUser {
		private final SimpleStringProperty screenName;
		private final SimpleStringProperty name;

		private ExceptionUser(String screenName, String name) {
			this.screenName = new SimpleStringProperty("@" + screenName);
			this.name = new SimpleStringProperty(name);
		}

		public String getScreenName() {
			return screenName.get();
		}

		public String getName() {
			return name.get();
		}

		public void setScreenName(String screenName) {
			this.screenName.set("@" + screenName);
		}

		public void setName(String name) {
			this.screenName.set(name);
		}
	}

	public static void main(String... args) {
		launch(args);
	}
}