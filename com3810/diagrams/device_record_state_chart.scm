{
  "graph": {
    "cells": [
      {
        "position": {
          "x": 0,
          "y": 0
        },
        "size": {
          "height": 10,
          "width": 10
        },
        "type": "Statechart",
        "id": "00ffb6d1-d225-4bc0-8b73-7df9987f57b7",
        "attrs": {
          "name": {
            "text": "device_record_state_chart Export"
          },
          "specification": {
            "text": "@EventDriven\n@SuperSteps(no)"
          }
        },
        "z": 1
      },
      {
        "position": {
          "x": -535,
          "y": -226
        },
        "size": {
          "height": 110,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "PENDING_REGISTRATION",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\npersistStatus(PENDING_REGISTRATION)"
          }
        },
        "id": "9b8c331a-9ab4-4b62-93a7-5a20c9761942",
        "z": 17
      },
      {
        "position": {
          "x": -621,
          "y": -207
        },
        "size": {
          "height": 18,
          "width": 18
        },
        "type": "Entry",
        "entryKind": "Initial",
        "attrs": {},
        "id": "1c0e1181-327a-45b2-a4fc-230153c1b5a7",
        "z": 20,
        "embeds": [
          "7fafa381-ce80-4087-872a-5a83864137c3"
        ]
      },
      {
        "type": "NodeLabel",
        "label": true,
        "size": {
          "width": 15,
          "height": 15
        },
        "position": {
          "x": -621,
          "y": -192
        },
        "attrs": {
          "label": {
            "refX": "50%",
            "textAnchor": "middle",
            "refY": "50%",
            "textVerticalAnchor": "middle"
          }
        },
        "id": "7fafa381-ce80-4087-872a-5a83864137c3",
        "z": 21,
        "parent": "1c0e1181-327a-45b2-a4fc-230153c1b5a7"
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "1c0e1181-327a-45b2-a4fc-230153c1b5a7"
        },
        "target": {
          "id": "9b8c331a-9ab4-4b62-93a7-5a20c9761942",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "1.401%",
              "dy": "31.405%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {},
            "position": {}
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "aeae10aa-7c42-485d-964e-cd1cb0364f44",
        "z": 22,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "position": {
          "x": -148,
          "y": 34
        },
        "size": {
          "width": 233,
          "height": 129
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "OFFLINE",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\npersistStatusOffline\n\ndms.heartbeatReceived /\nlastSeenAt = now();\npersistStatusOnline"
          }
        },
        "id": "232c0075-a456-4626-a542-f7d7079426f3",
        "z": 30
      },
      {
        "position": {
          "x": -563,
          "y": 12
        },
        "size": {
          "height": 130,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "ONLINE",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\npersistStatusOnline;\nlastSeenAt = now()\n\ndms.heartbeatReceived /\nlastSeenAt = now();\npersistStatusOnline"
          }
        },
        "id": "cfd52edb-c480-4cf0-94d4-a4f0b7992eeb",
        "z": 33
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "232c0075-a456-4626-a542-f7d7079426f3",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "10.898%",
              "dy": "96.429%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "cfd52edb-c480-4cf0-94d4-a4f0b7992eeb",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "82.99%",
              "dy": "75.177%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.heartbeatReceived"
              }
            },
            "position": {
              "distance": 0.4884784070386382,
              "offset": 13,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "f87eaa56-c3da-43c9-bf5d-9bb7af996687",
        "z": 34,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -122.61,
            "y": 215
          },
          {
            "x": -206,
            "y": 215
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "cfd52edb-c480-4cf0-94d4-a4f0b7992eeb",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "81.239%",
              "dy": "12.057%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "232c0075-a456-4626-a542-f7d7079426f3",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "51.555%",
              "dy": "23.571%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.heartbeatMissed\n[now()-lastSeenAt > heartbeatThresholdSeconds]"
              }
            },
            "position": {}
          },
          {
            "attrs": {
              "label": {
                "text": "2"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "4bc3790a-b483-4fef-8ec9-08975e770cd2",
        "z": 34,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -143,
            "y": -13
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "9b8c331a-9ab4-4b62-93a7-5a20c9761942"
        },
        "target": {
          "id": "cfd52edb-c480-4cf0-94d4-a4f0b7992eeb",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "28.714%",
              "dy": "8.511%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.registrationAccepted"
              }
            },
            "position": {
              "distance": 0.30851063829787234,
              "offset": 2.999990234374991,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "2"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "d0ee56d1-917b-43c9-851f-c0a2c37dad1b",
        "z": 35,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "position": {
          "x": -544,
          "y": 246
        },
        "size": {
          "width": 249,
          "height": 103
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "DEREGISTERED",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nrevokeMqttCredentials;\narchiveDeviceHistory"
          }
        },
        "id": "2312c07f-c059-4ebe-8c35-7996e5e4afd4",
        "z": 38
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "cfd52edb-c480-4cf0-94d4-a4f0b7992eeb"
        },
        "target": {
          "id": "2312c07f-c059-4ebe-8c35-7996e5e4afd4",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "46.572%",
              "dy": "14.876%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.deregisterRequested"
              }
            },
            "position": {
              "distance": 0.6096774193548387,
              "offset": 16.000006103515602,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "023a3f1c-acd5-4688-9559-1e1fbfeb353c",
        "z": 39,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "9b8c331a-9ab4-4b62-93a7-5a20c9761942"
        },
        "target": {
          "id": "2312c07f-c059-4ebe-8c35-7996e5e4afd4",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "33.966%",
              "dy": "75.207%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.deregisterRequested"
              }
            },
            "position": {
              "distance": 0.6605361780285829,
              "offset": -1,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "548edead-1a62-485b-8f61-158df335d7c6",
        "z": 39,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -612,
            "y": -169
          },
          {
            "x": -612,
            "y": 67
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "232c0075-a456-4626-a542-f7d7079426f3"
        },
        "target": {
          "id": "2312c07f-c059-4ebe-8c35-7996e5e4afd4",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "97.416%",
              "dy": "29.825%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.deregisterRequested"
              }
            },
            "position": {
              "distance": 0.6978464032496929,
              "offset": 14.916879879156655,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "2"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "ceead61f-e4c8-4f9e-9b2a-1c40f6b268b1",
        "z": 40,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -39,
            "y": 275.66
          }
        ]
      },
      {
        "position": {
          "x": -54,
          "y": 315
        },
        "size": {
          "height": 130,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "UNRESPONSIVE",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\npersistStatusUnresponsive;\nnotifyOwnerUnresponsive\n\ndms.heartbeatReceived /\nlastSeenAt = now();\npersistStatusOnline"
          }
        },
        "id": "6eda4ab6-dc9a-4794-9d5d-21c6fc7f24d5",
        "z": 41
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "6eda4ab6-dc9a-4794-9d5d-21c6fc7f24d5"
        },
        "target": {
          "id": "cfd52edb-c480-4cf0-94d4-a4f0b7992eeb",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "56.727%",
              "dy": "24.113%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.heartbeatReceived"
              }
            },
            "position": {
              "distance": 0.508776016848234,
              "offset": 16,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "6f1fba8d-8544-4938-aeae-83523024ce0d",
        "z": 42,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": 142,
            "y": -80
          },
          {
            "x": 1,
            "y": -80
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "232c0075-a456-4626-a542-f7d7079426f3"
        },
        "target": {
          "id": "6eda4ab6-dc9a-4794-9d5d-21c6fc7f24d5",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "33.266%",
              "dy": "14.894%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.extendedSilence\n[now()-lastSeenAt > unresponsiveThresholdHours*3600]"
              }
            },
            "position": {
              "distance": 0.3618421052631579,
              "offset": -38.99999938964844,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "3"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "b64972bd-cb1a-4b25-97c7-dc15050857ae",
        "z": 43,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "6eda4ab6-dc9a-4794-9d5d-21c6fc7f24d5"
        },
        "target": {
          "id": "2312c07f-c059-4ebe-8c35-7996e5e4afd4",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "58.921%",
              "dy": "92.982%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.deregisterRequested"
              }
            },
            "position": {}
          },
          {
            "attrs": {
              "label": {
                "text": "2"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "db63b304-94b9-4166-b942-80122ca24f61",
        "z": 44,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -259,
            "y": 388
          }
        ]
      }
    ]
  },
  "genModel": {
    "generator": {
      "type": "create::c",
      "features": {
        "Outlet": {
          "targetProject": "",
          "targetFolder": "",
          "libraryTargetFolder": "",
          "skipLibraryFiles": "",
          "apiTargetFolder": ""
        },
        "LicenseHeader": {
          "licenseText": ""
        },
        "FunctionInlining": {
          "inlineReactions": false,
          "inlineEntryActions": false,
          "inlineExitActions": false,
          "inlineEnterSequences": false,
          "inlineExitSequences": false,
          "inlineChoices": false,
          "inlineEnterRegion": false,
          "inlineExitRegion": false,
          "inlineEntries": false
        },
        "OutEventAPI": {
          "observables": false,
          "getters": false
        },
        "IdentifierSettings": {
          "moduleName": "Test",
          "statemachinePrefix": "test",
          "separator": "_",
          "headerFilenameExtension": "h",
          "sourceFilenameExtension": "c"
        },
        "Tracing": {
          "enterState": false,
          "exitState": false,
          "generic": false
        },
        "Includes": {
          "useRelativePaths": false,
          "generateAllSpecifiedIncludes": false
        },
        "GeneratorOptions": {
          "userAllocatedQueue": false,
          "metaSource": false
        },
        "GeneralFeatures": {
          "timerService": false,
          "timerServiceTimeType": ""
        },
        "Debug": {
          "dumpSexec": false
        }
      }
    }
  }
}