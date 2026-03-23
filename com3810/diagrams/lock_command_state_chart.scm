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
        "id": "sc-lock-root",
        "attrs": {
          "name": {
            "text": "lock_command_state_chart Export"
          },
          "specification": {
            "text": "@EventDriven\n@SuperSteps(no)\n\ninterface:\n\tvar deviceId: string\n\tvar correlationId: string\n\tvar commandType: string\n\tvar ackTimeoutSeconds: integer = 5\n\ninterface dms:\n\tin event commandReceived\n\tin event brokerConfirmed\n\tin event ackReceived\n\tin event ackFailed\n\tin event ackTimeout\n\tout event publishToMqtt\n\tout event startAckTimer\n\tout event writeStateToDb\n\tout event notifyClientSuccess\n\tout event notifyClientFailure\n\tout event markDeviceUnresponsive\n\tout event notifyOwnerUnresponsive"
          }
        },
        "z": 1
      },
      {
        "position": {
          "x": 60,
          "y": 100
        },
        "size": {
          "height": 15,
          "width": 15
        },
        "type": "Entry",
        "entryKind": "Initial",
        "attrs": {},
        "id": "lc-entry-initial",
        "embeds": [
          "lc-entry-initial-label"
        ],
        "z": 2
      },
      {
        "type": "NodeLabel",
        "label": true,
        "size": {
          "width": 15,
          "height": 15
        },
        "position": {
          "x": 60,
          "y": 115
        },
        "attrs": {
          "label": {
            "refX": "50%",
            "textAnchor": "middle",
            "refY": "50%",
            "textVerticalAnchor": "middle"
          }
        },
        "id": "lc-entry-initial-label",
        "parent": "lc-entry-initial",
        "z": 3
      },
      {
        "position": {
          "x": -72,
          "y": 663
        },
        "size": {
          "height": 110,
          "width": 240
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "AWAITING_ACK",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\n// waiting for device ack\n// or timeout"
          }
        },
        "id": "lc-state-awaiting",
        "z": 29
      },
      {
        "position": {
          "x": 159,
          "y": 141
        },
        "size": {
          "height": 100,
          "width": 240
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "IDLE",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\ncorrelationId = null;\ncommandType = null"
          }
        },
        "id": "lc-state-idle",
        "z": 35
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "lc-entry-initial"
        },
        "target": {
          "id": "lc-state-idle"
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
        "router": {
          "name": "orthogonal",
          "args": {
            "padding": 8
          }
        },
        "id": "lc-t-init-idle",
        "z": 36
      },
      {
        "position": {
          "x": 146,
          "y": 335
        },
        "size": {
          "height": 110,
          "width": 240
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "COMMAND_SENT",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\npublishToMqtt;\nstartAckTimer"
          }
        },
        "id": "lc-state-command-sent",
        "z": 37
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "lc-state-idle"
        },
        "target": {
          "id": "lc-state-command-sent"
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.commandReceived /\ncorrelationId = newUUID();\ncommandType = cmd"
              }
            },
            "position": {
              "offset": -75,
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
        "router": {
          "name": "orthogonal",
          "args": {
            "padding": 8
          }
        },
        "id": "lc-t-idle-sent",
        "z": 38
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "lc-state-command-sent"
        },
        "target": {
          "id": "lc-state-awaiting"
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.brokerConfirmed"
              }
            },
            "position": {
              "distance": 0.21196140646038952,
              "offset": -46,
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
        "router": {
          "name": "orthogonal",
          "args": {
            "padding": 8
          }
        },
        "id": "lc-t-sent-awaiting",
        "z": 38,
        "vertices": [
          {
            "x": -20,
            "y": 512
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "lc-state-awaiting"
        },
        "target": {
          "id": "lc-state-idle",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "87.956%",
              "dy": "66.667%",
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
                "text": "dms.ackFailed\n[incomingCorrelationId == correlationId\n && result == FAILURE] /\nnotifyClientFailure"
              }
            },
            "position": {
              "distance": 0.05169664944164917,
              "offset": 120.4364013671875,
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
        "id": "506a8abd-adc3-443d-92e3-e792a10638e8",
        "z": 44,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": 89,
            "y": 230
          }
        ]
      },
      {
        "position": {
          "x": 382,
          "y": 496
        },
        "size": {
          "height": 110,
          "width": 240
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "CONFIRMED",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nwriteStateToDb;\nnotifyClientSuccess"
          }
        },
        "id": "lc-state-confirmed",
        "z": 51
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "lc-state-awaiting"
        },
        "target": {
          "id": "lc-state-confirmed"
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.ackReceived\n[incomingCorrelationId == correlationId\n && result == SUCCESS]"
              }
            },
            "position": {
              "distance": 0.6579238429584248,
              "offset": -60,
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
        "router": {
          "name": "orthogonal",
          "args": {
            "padding": 8
          }
        },
        "vertices": [
          {
            "x": 7,
            "y": 579
          },
          {
            "x": 339,
            "y": 547
          }
        ],
        "id": "lc-t-awaiting-confirmed",
        "z": 52
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "lc-state-confirmed"
        },
        "target": {
          "id": "lc-state-idle"
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "after 0s"
              }
            },
            "position": {
              "distance": 0.6818563921329333,
              "offset": -17,
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
        "router": {
          "name": "orthogonal",
          "args": {
            "padding": 8
          }
        },
        "vertices": [
          {
            "x": 580,
            "y": 442
          },
          {
            "x": 551,
            "y": 199
          }
        ],
        "id": "lc-t-confirmed-idle",
        "z": 52
      },
      {
        "position": {
          "x": 379,
          "y": 667
        },
        "size": {
          "height": 110,
          "width": 240
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "TIMED_OUT",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nmarkDeviceUnresponsive;\nnotifyOwnerUnresponsive;\nnotifyClientFailure"
          }
        },
        "id": "lc-state-timedout",
        "z": 53
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "lc-state-command-sent"
        },
        "target": {
          "id": "lc-state-timedout"
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.ackTimeout\n[broker unreachable]"
              }
            },
            "position": {
              "distance": 0.6470492256345479,
              "offset": -22,
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
        "router": {
          "name": "orthogonal",
          "args": {
            "padding": 8
          }
        },
        "vertices": [
          {
            "x": 723,
            "y": 388
          },
          {
            "x": 723,
            "y": 725
          },
          {
            "x": 681,
            "y": 768
          }
        ],
        "id": "lc-t-sent-timedout",
        "z": 54
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "lc-state-awaiting"
        },
        "target": {
          "id": "lc-state-timedout"
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.ackTimeout\n[after ackTimeoutSeconds s]"
              }
            },
            "position": {
              "distance": 0.5486680700423869,
              "offset": 25,
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
        "router": {
          "name": "orthogonal",
          "args": {
            "padding": 8
          }
        },
        "vertices": [],
        "id": "lc-t-awaiting-timedout",
        "z": 54
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "lc-state-timedout"
        },
        "target": {
          "id": "lc-state-idle"
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "after 0s"
              }
            },
            "position": {
              "distance": 0.4940116286624502,
              "offset": 41,
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
        "router": {
          "name": "orthogonal",
          "args": {
            "padding": 8
          }
        },
        "vertices": [
          {
            "x": 648,
            "y": 714
          },
          {
            "x": 648,
            "y": 170
          }
        ],
        "id": "lc-t-timedout-idle",
        "z": 54
      }
    ]
  },
  "genModel": {
    "generator": {
      "type": "create::java",
      "features": {
        "Outlet": {
          "targetProject": "",
          "targetFolder": "",
          "libraryTargetFolder": "",
          "skipLibraryFiles": "",
          "apiTargetFolder": ""
        },
        "IdentifierSettings": {
          "moduleName": "LockCommandStateMachine",
          "statemachinePrefix": "lockCommand",
          "separator": "_"
        }
      }
    }
  }
}