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
        "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "attrs": {
          "name": {
            "text": "motion_event_state_chart Export"
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
          "y": -260
        },
        "size": {
          "height": 110,
          "width": 300
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "DETECTED",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nassignLamportSequence;\npersistSecurityEvent;\nsendStreamStart"
          }
        },
        "id": "state-detected",
        "z": 4
      },
      {
        "position": {
          "x": -560,
          "y": 130
        },
        "size": {
          "height": 110,
          "width": 300
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "CLOSED",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nsendStreamStop;\nfinalizeSecurityEvent;\nmarkEventComplete"
          }
        },
        "id": "state-closed",
        "z": 7
      },
      {
        "position": {
          "x": 7,
          "y": -83
        },
        "size": {
          "height": 130,
          "width": 300
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "MOTION_CLEARING",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nstartClearConfirmTimer\n\n// All sensors returned noMotion.\n// Waiting for grace period to confirm\n// motion has fully stopped."
          }
        },
        "id": "state-clearing",
        "z": 16
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-clearing",
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
          "id": "state-closed",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "100%",
              "dy": "30%",
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
                "text": "dms.clearConfirmExpired\n[all sensors still noMotion]"
              }
            },
            "position": {
              "offset": -18,
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
        "id": "trans-clearing-closed",
        "z": 17,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": 112,
            "y": 163
          }
        ]
      },
      {
        "position": {
          "x": -620,
          "y": -212
        },
        "size": {
          "height": 18,
          "width": 18
        },
        "type": "Entry",
        "entryKind": "Initial",
        "attrs": {},
        "id": "init-node-001",
        "z": 18,
        "embeds": [
          "init-label-001"
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
          "x": -620,
          "y": -197
        },
        "attrs": {
          "label": {
            "refX": "50%",
            "textAnchor": "middle",
            "refY": "50%",
            "textVerticalAnchor": "middle"
          }
        },
        "id": "init-label-001",
        "z": 19,
        "parent": "init-node-001"
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "init-node-001"
        },
        "target": {
          "id": "state-detected",
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
        "id": "trans-init-detected",
        "z": 20,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "position": {
          "x": -558,
          "y": -85
        },
        "size": {
          "height": 130,
          "width": 300
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "STREAMING",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nstartMotionPollTimer;\nstartMotionClearTimer\n\ndms.motionStillDetected /\nresetMotionClearTimer"
          }
        },
        "id": "state-streaming",
        "z": 21
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-detected",
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
          "id": "state-streaming",
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
                "text": "vss.streamConfirmedActive"
              }
            },
            "position": {
              "offset": 10,
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
        "id": "trans-detected-streaming",
        "z": 22,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-streaming",
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
          "id": "state-closed",
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
                "text": "dms.cameraOffline\n/ forceStreamStop"
              }
            },
            "position": {
              "offset": 14,
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
        "id": "trans-streaming-closed-forced",
        "z": 22,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-streaming",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "100%",
              "dy": "50%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-clearing",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "0%",
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
            "attrs": {
              "text": {
                "text": "dms.allSensorsNoMotion"
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
        "id": "trans-streaming-clearing",
        "z": 22,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-clearing"
        },
        "target": {
          "id": "state-streaming",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "96.211%",
              "dy": "79.433%",
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
                "text": "dms.motionReDetected\n[clearConfirmTimer still running]\n/ resetMotionClearTimer"
              }
            },
            "position": {
              "distance": 0.4849056603773585,
              "offset": -48.9999983215332,
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
        "id": "9dd300e1-8d06-4398-89c0-09d09c8e512e",
        "z": 23,
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