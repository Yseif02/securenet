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
        "id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
        "attrs": {
          "name": {
            "text": "notification_delivery_state_chart Export"
          },
          "specification": {
            "text": "@EventDriven\n@SuperSteps(no)"
          }
        },
        "z": 1
      },
      {
        "position": {
          "x": -560,
          "y": -340
        },
        "size": {
          "height": 100,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "PENDING",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nwriteOutboxRow(PENDING);\nattemptCount = 0"
          }
        },
        "id": "state-pending",
        "z": 4
      },
      {
        "position": {
          "x": -559,
          "y": -162
        },
        "size": {
          "height": 100,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "DISPATCHING",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nsendToApnsOrFcm(token, payload);\nstartResponseTimer"
          }
        },
        "id": "state-dispatching",
        "z": 40
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-pending",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "100%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-dispatching",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "0%",
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
                "text": "ns.dispatchPickedUp"
              }
            },
            "position": {
              "offset": 12,
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
        "id": "trans-pending-dispatching",
        "z": 41,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "position": {
          "x": -559,
          "y": 47
        },
        "size": {
          "height": 110,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "RETRYING",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nattemptCount = attemptCount + 1;\nstartBackoffTimer(attemptCount)"
          }
        },
        "id": "state-retrying",
        "z": 52
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-dispatching"
        },
        "target": {
          "id": "state-retrying",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "75.636%",
              "dy": "21.488%",
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
                "text": "ns.platformReturned5xx\n[or responseTimerExpired]"
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
        "id": "72397125-7c56-4d9c-84b9-5f7e55a5030f",
        "z": 53,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-retrying"
        },
        "target": {
          "id": "state-dispatching",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "23.461%",
              "dy": "86.486%",
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
                "text": "ns.backoffExpired\n[attemptCount < 5]"
              }
            },
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
        "id": "d60e2781-ab86-4eea-a6bc-d26379184afc",
        "z": 53,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "position": {
          "x": -564,
          "y": 237
        },
        "size": {
          "height": 110,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "FAILED",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nmarkOutboxRow(FAILED);\nraiseOperationsAlert"
          }
        },
        "id": "state-failed",
        "z": 54
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-retrying"
        },
        "target": {
          "id": "state-failed",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "56.027%",
              "dy": "11.57%",
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
                "text": "ns.backoffExpired\n[attemptCount >= 5]"
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
        "id": "e38d1e63-1802-46c2-ace8-b820b7b703df",
        "z": 55,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "position": {
          "x": -46,
          "y": -67
        },
        "size": {
          "height": 100,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "INVALID",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\ndeleteTokenFromRegistry;\nmarkOutboxRow(INVALID)"
          }
        },
        "id": "state-invalid",
        "z": 60
      },
      {
        "position": {
          "x": -42,
          "y": -214
        },
        "size": {
          "height": 100,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "SENT",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nmarkOutboxRow(SENT)"
          }
        },
        "id": "state-sent",
        "z": 62
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-dispatching",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "100%",
              "dy": "40%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-sent",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "0%",
              "dy": "40%",
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
                "text": "ns.platformReturned200"
              }
            },
            "position": {
              "offset": -14,
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
        "id": "trans-dispatching-sent",
        "z": 63,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-dispatching",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "96.646%",
              "dy": "80.18%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-invalid",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "9.805%",
              "dy": "65.766%",
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
                "text": "ns.tokenInvalidResponse\n[410 APNs / UNREGISTERED FCM]"
              }
            },
            "position": {
              "distance": 0.3379185776753615,
              "offset": 28.950155306735212,
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
        "id": "ac705a1b-a21b-4d9c-bdc8-389e829458be",
        "z": 64,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -116,
            "y": -81.82
          },
          {
            "x": -116,
            "y": -47
          }
        ]
      },
      {
        "position": {
          "x": -622,
          "y": -297
        },
        "size": {
          "height": 18,
          "width": 18
        },
        "type": "Entry",
        "entryKind": "Initial",
        "attrs": {},
        "id": "init-node-notif",
        "z": 68,
        "embeds": [
          "init-label-notif"
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
          "x": -622,
          "y": -282
        },
        "attrs": {
          "label": {
            "refX": "50%",
            "textAnchor": "middle",
            "refY": "50%",
            "textVerticalAnchor": "middle"
          }
        },
        "id": "init-label-notif",
        "z": 69,
        "parent": "init-node-notif"
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "init-node-notif"
        },
        "target": {
          "id": "state-pending",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "2%",
              "dy": "50%",
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
        "id": "trans-init-pending",
        "z": 70,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
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