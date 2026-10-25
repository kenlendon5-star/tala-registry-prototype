# Current questionnaire field dictionary

Generated from planning/prototype as web app/forms.js by tools/generate-reference.cjs. This is an implementation inventory, not an official questionnaire or approval of current defects. Date-dependent maxima follow the generation environment. Sections 4 and 18 are bespoke roster/consent workflows outside the field array.

## 1. Survey setup — household

Route: `setup`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| enumerator | Enumerator name | text | Yes | — | Always |
| barangay | Barangay | select | Yes | Barangay Cogon | Always |
| purok | Purok / zone | text | Yes | — | Always |
| interviewDate | Interview date | date | Yes | — | Always |

## 2. Visit information — household

Route: `visit`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| visitDate | Visit date | date | Yes | — | Always |
| visitResult | Visit result | select | Yes | Interview started; Completed visit; Callback; No respondent; Refused | Always |
| callbackDate | Callback appointment | datetime-local | Yes | — | a=>a.visitResult==='Callback' |
| visitNotes | Visit notes (Optional) | textarea | No | — | Always |

## 3. Household profile — household

Route: `household`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| head | Household Head | text | No | — | Always |
| firstName | First Name | text | Yes | — | Always |
| middleName | Middle Name (Optional) | text | No | — | Always |
| lastName | Last Name | text | Yes | — | Always |
| extension | Extension (Optional) | select | No | Jr.; Sr.; II; III; IV | Always |
| alias | Nickname / Alias (Optional) | text | No | — | Always |
| address | Address / purok | text | Yes | — | Always |
| respondent | Respondent name | text | Yes | — | Always |
| phone | Contact Number (Optional) | tel | No | — | Always |
| previousCity | Previous City (Optional) | text | No | — | Always |
| photo | Household Profile Picture (Optional) | photo | No | — | Always |
| houseSafe | Is Your House Safe? | choice | Yes | Yes; No | Always |
| safetyReason | Reason (Optional) | textarea | No | — | a=>!!a.houseSafe |

## 4. Household members — household

Route: `members`

Managed by workflow.js rather than generic fields.

## 5. Demographics — member

Route: `demographics`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| name | Full Name | text | No | — | Always |
| firstName | First Name | text | Yes | — | Always |
| middleName | Middle Name (Optional) | text | No | — | Always |
| lastName | Last Name | text | Yes | — | Always |
| extension | Extension (Optional) | select | No | Jr.; Sr.; II; III; IV | Always |
| alias | Nickname / Alias (Optional) | text | No | — | Always |
| phone | Contact Number (Optional) | tel | No | — | Always |
| registeredVoter | Registered Voter? | choice | Yes | Yes; No | Always |
| relationship | Relationship to household head | select | Yes | Head; Spouse; Daughter; Son; Parent; Other | Always |
| sex | Sex | choice | Yes | Male; Female | Always |
| dob | Date of birth | date | Yes | not future | Always |
| birth | Birth registered with the civil registry? | choice | Yes | Yes; No; Don't know | Always |
| civilStatus | Civil status | select | Yes | Single; Married; Widowed; Separated; Other; Prefer not to answer | Always |

## 6. Other demographics — member

Route: `other`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| nationalId | Has a National ID? | choice | Yes | Yes; No | Always |
| soloParent | Identifies as a Solo Parent? | choice | Yes | Yes; No | Always |
| soloParentId | Solo Parent ID (Optional) | text | No | — | a=>a.soloParent==='Yes' |
| disability | Has Disability? | choice | Yes | Yes; No | Always |
| registeredPwd | Registered PWD? | choice | Yes | Yes; No | a=>a.disability==='Yes' |
| pwdId | PWD / Disability ID | text | Yes | — | a=>a.disability==='Yes'&&a.registeredPwd==='Yes' |
| disabilityDetail | Disability description | text | Yes | — | a=>a.disability==='Yes' |
| seniorId | Has a senior citizen ID? | choice | Yes | Yes; No; Not applicable | Always |

## 7. Migration — member

Route: `migration`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| citizenship | Citizenship | text | Yes | — | Always |
| moved | Moved into this barangay? | choice | Yes | Yes; No | Always |
| previousResidence | Previous city / municipality | text | Yes | — | a=>a.moved==='Yes' |
| moveYear | Year moved | number | Yes | min 1900; max 2026 | a=>a.moved==='Yes' |
| overseas | Currently residing overseas? | choice | Yes | Yes; No | Always |

## 8. Education — member

Route: `education`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| attending | Currently attending school? | choice | Yes | Yes; No | Always |
| school | Type of school | select | Yes | Public; Private; Special Needs Education (SNED); Special Education (SPED) | a=>a.attending==='Yes' |
| reason | Reason for not attending | select | Yes | Not yet school age; Completed schooling; Financial concerns; Working; Other; Prefer not to answer | a=>a.attending==='No' |
| grade | Highest grade / year completed | select | Yes | No grade completed; Kindergarten; Grade 1; Grade 2; Grade 3; Grade 4; Grade 5; Grade 6; Grade 7; Grade 8; Grade 9; Grade 10; Grade 11; Grade 12; College level; College graduate | Always |
| educationAssistance | Did You Receive Educational Assistance? | choice | Yes | Yes; No | Always |
| track | Program / track (optional) | text | No | — | a=>a.attending==='Yes' |

## 9. Employment — member

Route: `employment`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| worked | Did This Member Earn for Pay or Profit in the Past 7 Days? | choice | Yes | Yes; No | Always |
| occupation | Occupation / job title | text | Yes | — | a=>a.worked==='Yes' |
| industry | Industry / business activity | text | Yes | — | a=>a.worked==='Yes' |
| employmentType | Employment type | select | Yes | Employee; Self-employed; Employer; Paid family worker | a=>a.worked==='Yes' |
| hours | Hours worked in the past 7 days | number | Yes | min 0; max 168 | a=>a.worked==='Yes' |
| looking | Currently looking for work? | choice | Yes | Yes; No | a=>a.worked==='No' |
| notWorkingReason | Main reason for not working | select | Yes | Student; Below working age; Homemaking / caregiving; Retired; Health condition; Unable to find work; Other | a=>a.worked==='No' |
| available | Available to start work? | choice | Yes | Yes; No | a=>a.worked==='No'&&a.looking==='Yes' |

## 10. Health — household

Route: `health`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| illness | Did anyone need medical care in the past 30 days? | choice | Yes | Yes; No | Always |
| treatment | Where was care sought? | select | Yes | Barangay health center; Public hospital; Private facility; No care sought; Other | a=>a.illness==='Yes' |
| healthAccess | Can the household reach a health facility when needed? | choice | Yes | Yes; No | Always |

## 11. Food security — household

Route: `food`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| foodWorry | In the past 30 days, how often did the household worry about food? | select | Yes | Never; Rarely; Sometimes; Often | Always |
| skippedMeals | Did anyone skip a meal due to lack of resources? | choice | Yes | Yes; No | Always |
| foodAssistance | Received food assistance in the past 30 days? | choice | Yes | Yes; No | Always |

## 12. Transport & finance — household

Route: `transport`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| transport | Usual mode of transport | select | Yes | Walking; Bicycle; Motorcycle; Tricycle; Jeepney / bus; Private car; Other | Always |
| accounts | Financial accounts held | multi | Yes | Bank; Digital bank; E-money; Cooperative; None; Prefer not to answer | Always |

## 13. Disaster preparedness — household

Route: `disaster`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| disasterType | Type of Disaster | select | Yes | Man-made; Natural; Biological Disaster | Always |
| hazards | Hazards experienced in the past year | multi | Yes | Flood; Typhoon; Earthquake; Fire; Landslide; None | Always |
| evacuationKnown | Knows the designated evacuation area? | choice | Yes | Yes; No | Always |
| evacuation | Identified Evacuation Area | select | Yes | Barangay Hall (Sample); School Evacuation Center (Sample); Covered Court (Sample); Other | a=>a.evacuationKnown==='Yes' |
| evacuationOther | Other Evacuation Area | text | Yes | — | a=>a.evacuationKnown==='Yes'&&a.evacuation==='Other' |
| emergencyKit | Has an emergency kit? | choice | Yes | Yes; No | Always |

## 14. Internet & public safety — household

Route: `internet`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| internet | Does anyone have internet access? | choice | Yes | Yes; No | Always |
| connection | Internet connection | select | Yes | Mobile data; Fiber / cable; Public Wi-Fi; Other | a=>a.internet==='Yes' |
| safety | How safe does the household feel in the neighborhood? | select | Yes | Very safe; Somewhat safe; Somewhat unsafe; Very unsafe; Prefer not to answer | Always |

## 15. Social protection — household

Route: `protection`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| philHealth | Covered by PhilHealth? | choice | Yes | Yes; No | Always |
| philHealthRole | PhilHealth Coverage | choice | Yes | Member; Dependent | a=>a.philHealth==='Yes' |
| dswd | Receives DSWD Assistance? | choice | Yes | Yes; No | Always |
| fourPs | 4Ps Beneficiary? | choice | Yes | Yes; No | Always |
| programs | Programs covering household members | multi | Yes | PhilHealth; SSS; GSIS; 4Ps; Senior citizen assistance; PWD assistance; None; Prefer not to answer | Always |
| assistance | Received other government assistance in the past year? | choice | Yes | Yes; No | Always |
| assistanceDetail | Type of assistance | text | Yes | — | a=>a.assistance==='Yes' |

## 16. Water & sanitation — household

Route: `water`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| water | Main drinking water source | select | Yes | Piped into dwelling; Piped to yard or plot; Public tap or standpipe; Tube well or borehole; Protected well; Spring; Rainwater; Tanker truck; Other | Always |
| toilet | Toilet facility | select | Yes | Flush / pour flush; Pit latrine; Shared toilet; No toilet; Other | Always |
| waste | Waste disposal practices | multi | Yes | Collection service; Composting; Recycling; Burning; Other | Always |
| handwashing | Has a handwashing place with soap and water? | choice | Yes | Yes; No | Always |

## 17. Housing characteristics — household

Route: `housing`

| Key | Label | Control | Required when visible | Options / bounds | Visibility |
|---|---|---|---|---|---|
| roof | Main roofing material | select | Yes | Metal roofing sheets; Concrete / clay / slate tile; Wood / bamboo; Sod / thatch; Makeshift / salvaged; Other | Always |
| walls | Main wall material | select | Yes | Concrete / brick; Wood; Bamboo; Mixed materials; Other | Always |
| tenure | Housing tenure | select | Yes | Owned; Rented; Rent-free with permission; Other | Always |
| electricity | Has electricity? | choice | Yes | Yes; No | Always |
| rooms | Number of sleeping rooms | number | Yes | min 0; max 50 | Always |

## 18. Consent & review — household

Route: `consent`

Managed by workflow.js rather than generic fields.

## Bespoke stored values

Consent stores consent, drawn signature or signedName, and reviewedAt after review. The roster stores name/relationship and other member answers; IDs are currently household codes plus member array position. Browser location stores lat/lng, source, accuracy, capturedAt, verifiedAt, notes and addressAtCapture. Profile stores name, barangay and optional photo. These shapes are browser legacy data, not the proposed portable backup format.
